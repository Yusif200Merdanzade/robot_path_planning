package com.example.robotpathplanning.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rate limiting-in HTTP qatı: sorğunu RateLimiter-in qərarına çevirir, qərarı HTTP cavabına.
 *
 *   icazə   -> sorğu davam edir, X-RateLimit-Remaining başlığı əlavə olunur
 *   rədd    -> 429 + Retry-After + RFC 9457 (application/problem+json) gövdəsi; sonrakı filtrlər işləmir
 *
 * Security zəncirində ən əvvəldə dayanır: rədd olunan sorğu üçün nə token, nə parol yoxlanır.
 *
 * Klientin IP-si request.getRemoteAddr()-dən götürülür. Serverdə (Render) sorğu proxy-dən keçir:
 * prod-da server.forward-headers-strategy=native əsl IP-ni X-Forwarded-For-un proxy tərəfindən əlavə
 * olunmuş (saxtalaşdırıla bilməyən) hissəsindən bərpa edir.
 *
 * @Component deyil: Spring Boot onu ikinci dəfə adi servlet filtri kimi qeydiyyata almasın.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final int IPV6_PREFIX_BYTES = 8;   // /64

    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    // Hücum zamanı hər rədd olunan sorğunu loglamaq logun özünü hücum hədəfinə çevirir:
    // eyni (qayda, klient) üçün dəqiqədə ən çox bir xəbərdarlıq yazılır
    private final Cache<String, Boolean> recentlyLogged = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    public RateLimitFilter(RateLimiter rateLimiter, JsonMapper json) {
        this.rateLimiter = rateLimiter;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String client = clientKey(request.getRemoteAddr());

        RateLimiter.Decision decision = rateLimiter.tryConsume(request.getMethod(), path, client);
        if (!decision.allowed()) {
            logRejection(decision, client, request.getMethod(), path);
            reject(response, decision, request.getRequestURI());
            return;
        }
        decision.remaining().ifPresent(left -> response.setHeader(REMAINING_HEADER, String.valueOf(left)));
        chain.doFilter(request, response);
    }

    /**
     * Limitin kimə aid olduğu: klientin IP-si.
     *
     * IPv6-da bir istifadəçiyə (ev, VPS) adətən bütöv /64 şəbəkə verilir — milyardlarla ünvan.
     * Hər ünvana ayrı limit versək, hücumçu sadəcə ünvanını dəyişərək limiti keçər.
     * Ona görə IPv6 ünvanları /64 prefiksinə qədər qruplaşdırılır. IPv4 olduğu kimi qalır.
     */
    static String clientKey(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
        if (!remoteAddress.contains(":")) return remoteAddress;   // IPv4
        try {
            // IP literalı üçün getByName DNS-ə müraciət etmir, sadəcə parse edir
            InetAddress address = InetAddress.getByName(remoteAddress);
            if (!(address instanceof Inet6Address)) return address.getHostAddress();   // IPv4-mapped IPv6
            byte[] bytes = address.getAddress();
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < IPV6_PREFIX_BYTES; i += 2) {
                prefix.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
            }
            return prefix.append(":/64").toString();
        } catch (UnknownHostException e) {
            return remoteAddress;
        }
    }

    private void reject(HttpServletResponse response, RateLimiter.Decision decision, String instance) throws IOException {
        long retryAfter = decision.retryAfterSeconds();
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Too Many Requests");
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", "Çox sorğu göndərildi. " + retryAfter + " saniyə sonra yenidən cəhd edin.");
        problem.put("instance", instance);
        problem.put("retryAfterSeconds", retryAfter);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setHeader(REMAINING_HEADER, "0");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.writeValueAsString(problem));
    }

    private void logRejection(RateLimiter.Decision decision, String client, String method, String path) {
        if (recentlyLogged.asMap().putIfAbsent(decision.rule() + "|" + client, Boolean.TRUE) == null) {
            log.warn("Sorğu limiti keçildi: qayda={} klient={} {} {} (retry-after {}s; bu klient üçün 1 dəq ərzində təkrar loglanmır)",
                    decision.rule(), client, method, path, decision.retryAfterSeconds());
        }
    }
}
