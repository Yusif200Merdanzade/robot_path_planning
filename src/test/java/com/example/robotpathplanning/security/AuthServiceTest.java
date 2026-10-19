package com.example.robotpathplanning.security;

import com.example.robotpathplanning.security.captcha.CaptchaException;
import com.example.robotpathplanning.security.captcha.CaptchaVerifier;
import com.example.robotpathplanning.security.dto.JwtRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    private static final AuthProperties AUTH = new AuthProperties("robot", "secret",
            "x".repeat(64), Duration.ofHours(1));

    private static AuthService service(CaptchaVerifier.Result captchaResult, AtomicReference<String> seenIp) {
        CaptchaVerifier verifier = (token, ip) -> {
            seenIp.set(ip);
            return captchaResult;
        };
        return new AuthService(AUTH, new JwtProvider(AUTH), verifier);
    }

    @Test
    void validCaptchaAndPasswordGiveToken() {
        AtomicReference<String> ip = new AtomicReference<>();
        var response = service(CaptchaVerifier.Result.PASSED, ip).login(new JwtRequest("robot", "secret", "t"), "1.2.3.4");
        assertNotNull(response.accessToken());
        assertEquals("1.2.3.4", ip.get(), "klientin IP-si CAPTCHA provayderinə ötürülməlidir");
    }

    @Test
    void failedCaptchaStopsLoginEvenWithCorrectPassword() {
        AuthService service = service(CaptchaVerifier.Result.REJECTED, new AtomicReference<>());
        CaptchaException e = assertThrows(CaptchaException.class,
                () -> service.login(new JwtRequest("robot", "secret", "bad"), "1.2.3.4"));
        assertEquals(CaptchaVerifier.Result.REJECTED, e.result());
    }

    @Test
    void captchaIsCheckedBeforePassword() {
        // Parol da səhvdir, amma cavab CAPTCHA xətasıdır: bot parol haqqında heç nə öyrənmir
        AuthService service = service(CaptchaVerifier.Result.REJECTED, new AtomicReference<>());
        assertThrows(CaptchaException.class, () -> service.login(new JwtRequest("robot", "wrong", "bad"), "1.2.3.4"));
    }

    @Test
    void unavailableCaptchaFailsClosed() {
        AuthService service = service(CaptchaVerifier.Result.UNAVAILABLE, new AtomicReference<>());
        CaptchaException e = assertThrows(CaptchaException.class,
                () -> service.login(new JwtRequest("robot", "secret", "t"), "1.2.3.4"));
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, e.result());
    }

    @Test
    void wrongPasswordAfterValidCaptchaIsBadCredentials() {
        AuthService service = service(CaptchaVerifier.Result.PASSED, new AtomicReference<>());
        assertThrows(BadCredentialsException.class, () -> service.login(new JwtRequest("robot", "wrong", "t"), "1.2.3.4"));
    }
}
