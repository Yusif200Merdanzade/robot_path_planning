package com.example.robotpathplanning.security.dto;

/**
 * POST /api/auth/login sorğusunun gövdəsi:
 * {"username": "admin", "password": "...", "captchaToken": "..."}
 *
 * captchaToken - login səhifəsindəki CAPTCHA vidcetinin verdiyi birdəfəlik token.
 */
public record JwtRequest(String username, String password, String captchaToken) {
}
