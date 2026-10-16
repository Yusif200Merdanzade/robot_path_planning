package com.example.robotpathplanning.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT ilə giriş (digər layihələrdəki model): sessiya yoxdur, hər sorğu tokenlə gəlir.
 *
 *   POST /api/auth/login        - açıq, token verir
 *   index.html, login.html      - açıq (sadəcə səhifə qabığıdır, token olmadan heç bir məlumat göstərmir)
 *   /api/** və /ws/robot        - yalnız düzgün tokenlə; token yoxdursa 401
 *
 * Səhifə öz-özünə qorunmur, çünki brauzer adi keçiddə Authorization başlığı göndərmir.
 * Token yoxdursa index.html istifadəçini özü login.html-ə göndərir.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/", "/index.html", "/login.html", "/favicon.ico", "/error", "/api/auth/login"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
        return http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)   // cookie/sessiya yoxdur — CSRF riski yoxdur
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
