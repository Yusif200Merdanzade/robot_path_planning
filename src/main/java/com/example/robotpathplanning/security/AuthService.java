package com.example.robotpathplanning.security;

import com.example.robotpathplanning.security.captcha.CaptchaException;
import com.example.robotpathplanning.security.captcha.CaptchaVerifier;
import com.example.robotpathplanning.security.dto.JwtRequest;
import com.example.robotpathplanning.security.dto.JwtResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Girişin ardıcıllığı: əvvəl CAPTCHA, sonra istifadəçi adı və parol; hamısı düzdürsə token.
 *
 * CAPTCHA keçilməyibsə parola heç baxılmır: bot parol sınamağa başlaya bilmir.
 *
 * İstifadəçi hələlik statikdir (AuthProperties). Baza əlavə olunanda yalnız bu sinif dəyişəcək
 * (istifadəçi bazadan oxunacaq, parol BCrypt ilə yoxlanacaq), API isə eyni qalacaq.
 */
@Service
public class AuthService {

    private final AuthProperties properties;
    private final JwtProvider jwtProvider;
    private final CaptchaVerifier captchaVerifier;

    public AuthService(AuthProperties properties, JwtProvider jwtProvider, CaptchaVerifier captchaVerifier) {
        this.properties = properties;
        this.jwtProvider = jwtProvider;
        this.captchaVerifier = captchaVerifier;
    }

    /**
     * @param clientIp istifadəçinin IP-si (CAPTCHA provayderinə risk qiymətləndirməsi üçün ötürülür)
     * @throws CaptchaException        CAPTCHA keçilmədi və ya yoxlana bilmədi
     * @throws BadCredentialsException istifadəçi adı və ya parol səhvdir
     */
    public JwtResponse login(JwtRequest request, String clientIp) {
        CaptchaVerifier.Result captcha = captchaVerifier.verify(request == null ? null : request.captchaToken(), clientIp);
        if (captcha != CaptchaVerifier.Result.PASSED) {
            throw new CaptchaException(captcha);
        }
        if (!credentialsMatch(request)) {
            throw new BadCredentialsException("İstifadəçi adı və ya parol səhvdir");
        }
        return JwtResponse.bearer(jwtProvider.generateToken(properties.username()));
    }

    private boolean credentialsMatch(JwtRequest request) {
        if (request == null || request.username() == null || request.password() == null) return false;
        // & (&& yox): hər iki müqayisə həmişə aparılır, cavab vaxtından hansının səhv olduğu bilinmir
        return constantTimeEquals(request.username(), properties.username())
                & constantTimeEquals(request.password(), properties.password());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
