package com.example.robotpathplanning.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Hər sorğudan JWT tokeni çıxarır və düzdürsə istifadəçini "daxil olmuş" kimi qeyd edir.
 *
 * Token haradan gəlir:
 *   REST       - "Authorization: Bearer <token>" başlığı
 *   WebSocket  - /ws/robot?token=<token> (brauzerin WebSocket API-si başlıq göndərə bilmir)
 *
 * Token yoxdursa sorğu sadəcə davam edir: açıq ünvanlar işləyir, qorunanları SecurityConfig 401 ilə qaytarır.
 * Token var, amma səhvdirsə və ya vaxtı keçibsə dərhal 401 qaytarılır.
 *
 * @Component deyil: Spring Boot onu ikinci dəfə adi servlet filtri kimi qeydiyyata almasın.
 */
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String WEBSOCKET_PATH = "/ws/robot";

    private final JwtProvider jwtProvider;

    public JwtFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = extractToken(request);
        if (token.isPresent()) {
            Optional<String> username = jwtProvider.getUsername(token.get());
            if (username.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token səhvdir və ya vaxtı keçib");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username.get(), null, List.of()));
        }
        chain.doFilter(request, response);
    }

    private static Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER)) {
            return Optional.of(header.substring(BEARER.length()));
        }
        String query = request.getParameter("token");
        if (StringUtils.hasText(query) && request.getRequestURI().endsWith(WEBSOCKET_PATH)) {
            return Optional.of(query);
        }
        return Optional.empty();
    }
}
