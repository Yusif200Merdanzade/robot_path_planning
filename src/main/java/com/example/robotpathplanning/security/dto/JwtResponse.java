package com.example.robotpathplanning.security.dto;

/** Uğurlu girişin cavabı: {"type": "Bearer", "accessToken": "eyJ..."} */
public record JwtResponse(String type, String accessToken) {

    public static JwtResponse bearer(String accessToken) {
        return new JwtResponse("Bearer", accessToken);
    }
}
