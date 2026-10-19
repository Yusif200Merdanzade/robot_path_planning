package com.example.robotpathplanning.security;

import com.example.robotpathplanning.security.captcha.CaptchaException;
import com.example.robotpathplanning.security.captcha.CaptchaProperties;
import com.example.robotpathplanning.security.captcha.CaptchaVerifier;
import com.example.robotpathplanning.security.captcha.TurnstileVerifier;
import com.example.robotpathplanning.security.dto.JwtRequest;
import com.example.robotpathplanning.security.dto.JwtResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET  /api/auth/captcha - login səhifəsi CAPTCHA vidcetini qurmaq üçün açıq parametrləri alır
 *                          {"enabled": true, "provider": "turnstile", "siteKey": "...", "action": "login"}
 *
 * POST /api/auth/login
 * sorğu: {"username": "admin", "password": "...", "captchaToken": "..."}
 * cavab: 200 {"type": "Bearer", "accessToken": "eyJ..."}
 *        400 {"code": "captcha_rejected", ...}    - CAPTCHA keçilmədi
 *        401                                       - istifadəçi adı və ya parol səhvdir (Spring Security)
 *        503 {"code": "captcha_unavailable", ...} - CAPTCHA provayderi cavab vermədi (giriş bağlıdır)
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final CaptchaProperties captcha;

    public AuthController(AuthService authService, CaptchaProperties captcha) {
        this.authService = authService;
        this.captcha = captcha;
    }

    /** Yalnız açıq məlumat: siteKey onsuz da səhifədə görünür, gizli açar heç vaxt buraya düşmür. */
    public record CaptchaConfigResponse(boolean enabled, String provider, String siteKey, String action) {
    }

    @GetMapping("/api/auth/captcha")
    public CaptchaConfigResponse captchaConfig() {
        return captcha.enabled()
                ? new CaptchaConfigResponse(true, "turnstile", captcha.siteKey(), TurnstileVerifier.LOGIN_ACTION)
                : new CaptchaConfigResponse(false, null, null, null);
    }

    @PostMapping("/api/auth/login")
    public JwtResponse login(@RequestBody JwtRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    /** Səhifə "code"-a görə mesaj seçir və vidceti yeniləyir. */
    @ExceptionHandler(CaptchaException.class)
    public ResponseEntity<ProblemDetail> captchaFailed(CaptchaException e) {
        boolean unavailable = e.result() == CaptchaVerifier.Result.UNAVAILABLE;
        HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle("Robot yoxlaması");
        problem.setProperty("code", unavailable ? "captcha_unavailable" : "captcha_rejected");
        return ResponseEntity.status(status).body(problem);
    }
}
