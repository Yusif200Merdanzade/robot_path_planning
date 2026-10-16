package com.example.robotpathplanning.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Girişin bütün parametrləri bir yerdə (app.auth.*, bax: application.properties).
 *
 * Səhv parametrlə proqram işə düşməsin deyə hamısı başlanğıcda yoxlanır:
 * səhv birinci girişdə yox, deploy anında görünsün.
 *
 * @param username  statik istifadəçinin adı
 * @param password  statik istifadəçinin parolu
 * @param jwtSecret JWT imza açarı (HS512 üçün ən azı 64 bayt)
 * @param tokenTtl  tokenin ömrü (məs. 1d, 8h)
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String username, String password, String jwtSecret, Duration tokenTtl) {

    private static final int MIN_SECRET_BYTES = 64;

    public AuthProperties {
        requireValue("app.auth.username (APP_USERNAME)", username);
        requireValue("app.auth.password (APP_PASSWORD)", password);
        requireValue("app.auth.jwt-secret (JWT_SECRET)", jwtSecret);
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.auth.jwt-secret (JWT_SECRET) ən azı " + MIN_SECRET_BYTES + " simvol olmalıdır");
        }
        if (tokenTtl == null) tokenTtl = Duration.ofDays(1);
    }

    /** Boş dəyər və ya açılmamış "${...}" (mühit dəyişəni verilməyib) qəbul olunmur. */
    private static void requireValue(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " təyin olunmayıb");
        }
    }
}
