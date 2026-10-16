package com.example.robotpathplanning.security;

import com.example.robotpathplanning.security.dto.JwtRequest;
import com.example.robotpathplanning.security.dto.JwtResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/login
 * sorğu: {"username": "admin", "password": "..."}
 * cavab: 200 {"type": "Bearer", "accessToken": "eyJ..."} və ya 401
 *
 * Səhv parolda AuthService BadCredentialsException atır; onu Spring Security özü tutub
 * 401-ə çevirir (bax: SecurityConfig, authenticationEntryPoint) — ayrıca handler lazım deyil.
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    public JwtResponse login(@RequestBody JwtRequest request) {
        return authService.login(request);
    }
}
