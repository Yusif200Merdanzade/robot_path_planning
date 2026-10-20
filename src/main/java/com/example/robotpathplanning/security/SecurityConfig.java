package com.example.robotpathplanning.security;

import com.example.robotpathplanning.security.captcha.CaptchaProperties;
import com.example.robotpathplanning.security.ratelimit.RateLimitFilter;
import com.example.robotpathplanning.security.ratelimit.RateLimitProperties;
import com.example.robotpathplanning.security.ratelimit.RateLimiter;
import io.github.bucket4j.TimeMeter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * JWT ilə giriş (digər layihələrdəki model): sessiya yoxdur, hər sorğu tokenlə gəlir.
 *
 *   POST /api/auth/login        - açıq, token verir (CAPTCHA + parol)
 *   GET  /api/auth/captcha      - açıq, CAPTCHA vidcetinin açıq parametrləri
 *   /api/** və /ws/**           - yalnız düzgün tokenlə; token yoxdursa 401
 *   qalan hər şey               - açıq: səhifələr (index.html, login.html) sadəcə qabıqdır, məlumatı
 *                                 yalnız API/WebSocket verir. Mövcud olmayan ünvan 401 yox, 404 alır.
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

    /**
     * Content-Security-Policy: brauzer yalnız bu serverdən kod/stil/əlaqə qəbul edir, səhifə başqa saytın
     * içində (iframe) açılmır, plugin (object) yüklənmir.
     *
     * 'unsafe-inline' hələlik lazımdır: index.html/login.html-də skript və stillər səhifənin içindədir
     * (onclick="..." də daxil). Onlar ayrıca .js fayllarına köçürüləndə 'unsafe-inline' script-src-dən
     * çıxarılmalıdır — XSS-ə qarşı əsas qazanc o zaman olacaq.
     * connect-src 'self' həm fetch-i, həm eyni hostdakı ws:/wss: əlaqəsini əhatə edir.
     * CAPTCHA aktivdirsə, yalnız seçilmiş provayderin mənbələri əlavə olunur (skript və vidcetin iframe-i).
     */
    static String contentSecurityPolicy(CaptchaProperties captcha) {
        String captchaScripts = captcha.enabled() ? " " + String.join(" ", captcha.provider().scriptSources()) : "";
        String captchaFrames = captcha.enabled() ? String.join(" ", captcha.provider().frameSources()) : "'none'";
        return String.join("; ",
                "default-src 'self'",
                "script-src 'self' 'unsafe-inline'" + captchaScripts,
                "frame-src " + captchaFrames,
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data:",
                "connect-src 'self'",
                "object-src 'none'",
                "base-uri 'self'",
                "form-action 'self'",
                "frame-ancestors 'none'");
    }

    /** Vedrələr proses yaddaşında saxlanır (Redis-ə keçid yalnız bu metodu dəyişir — bax: RateLimiter). */
    @Bean
    public RateLimiter rateLimiter(RateLimitProperties rateLimits) {
        TimeMeter clock = TimeMeter.SYSTEM_MILLISECONDS;
        return new RateLimiter(rateLimits, RateLimiter.inMemoryBuckets(rateLimits.maxTrackedClients(), clock), clock);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   RateLimitProperties rateLimits, RateLimiter rateLimiter,
                                                   JsonMapper json, CaptchaProperties captcha) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)   // cookie/sessiya yoxdur — CSRF riski yoxdur
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll()
                        .requestMatchers("/api/**", "/ws/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Spring Security standart olaraq X-Content-Type-Options, X-Frame-Options, Cache-Control və
                // (HTTPS-də) HSTS əlavə edir; üstünə CSP, Referrer-Policy və Permissions-Policy
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy(captcha)))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .addFilterBefore(new JwtFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        if (rateLimits.enabled()) {
            // Limit hər şeydən əvvəl: rədd olunan sorğu üçün nə token, nə parol yoxlanır
            http.addFilterBefore(new RateLimitFilter(rateLimiter, json), JwtFilter.class);
        }
        return http.build();
    }
}
