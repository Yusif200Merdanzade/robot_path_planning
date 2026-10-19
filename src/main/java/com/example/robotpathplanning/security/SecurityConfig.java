package com.example.robotpathplanning.security;

import com.example.robotpathplanning.security.ratelimit.RateLimitFilter;
import com.example.robotpathplanning.security.ratelimit.RateLimitProperties;
import com.example.robotpathplanning.security.ratelimit.RateLimiter;
import io.github.bucket4j.TimeMeter;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * JWT ilə giriş (digər layihələrdəki model): sessiya yoxdur, hər sorğu tokenlə gəlir.
 *
 *   POST /api/auth/login        - açıq, token verir
 *   index.html, login.html      - açıq (sadəcə səhifə qabığıdır, token olmadan heç bir məlumat göstərmir)
 *   /api/** və /ws/robot        - yalnız düzgün tokenlə; token yoxdursa 401
 *   limitli ünvanlar            - IP üzrə sorğu limiti keçilibsə 429 (bax: ratelimit paketi)
 *
 * Filtrlərin sırası: RateLimitFilter -> JwtFilter -> ... -> controller
 *
 * Səhifə öz-özünə qorunmur, çünki brauzer adi keçiddə Authorization başlığı göndərmir.
 * Token yoxdursa index.html istifadəçini özü login.html-ə göndərir.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/", "/index.html", "/login.html", "/favicon.ico", "/error", "/api/auth/login"
    };

    /** Vedrələr proses yaddaşında saxlanır (Redis-ə keçid yalnız bu metodu dəyişir — bax: RateLimiter). */
    @Bean
    public RateLimiter rateLimiter(RateLimitProperties rateLimits) {
        TimeMeter clock = TimeMeter.SYSTEM_MILLISECONDS;
        return new RateLimiter(rateLimits, RateLimiter.inMemoryBuckets(rateLimits.maxTrackedClients(), clock), clock);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   RateLimitProperties rateLimits, RateLimiter rateLimiter,
                                                   JsonMapper json) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)   // cookie/sessiya yoxdur — CSRF riski yoxdur
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        if (rateLimits.enabled()) {
            // Limit hər şeydən əvvəl: rədd olunan sorğu üçün nə token, nə parol yoxlanır
            http.addFilterBefore(new RateLimitFilter(rateLimiter, json), JwtFilter.class);
        }
        return http.build();
    }
}
