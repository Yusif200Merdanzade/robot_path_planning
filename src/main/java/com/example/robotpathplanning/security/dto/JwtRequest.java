package com.example.robotpathplanning.security.dto;

/** POST /api/auth/login sorğusunun gövdəsi: {"username": "admin", "password": "..."} */
public record JwtRequest(String username, String password) {
}
