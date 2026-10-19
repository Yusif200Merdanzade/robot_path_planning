package com.example.robotpathplanning.security.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimitFilterTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String CONTEXT = "/robot-path-planning";

    private final JsonMapper json = JsonMapper.builder().build();
    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter(
            new RateLimitProperties(true, 1000, List.of(new RateLimitProperties.Rule("login", "POST", LOGIN,
                    List.of(new RateLimitProperties.Limit(2, Duration.ofMinutes(1)))))),
            RateLimiter.inMemoryBuckets(1000, TimeMeter.SYSTEM_MILLISECONDS), TimeMeter.SYSTEM_MILLISECONDS), json);

    @Test
    void allowedRequestPassesThroughWithRemainingHeader() throws Exception {
        Result result = send("POST", LOGIN, "1.1.1.1");
        assertEquals(200, result.response.getStatus());
        assertEquals("1", result.response.getHeader(RateLimitFilter.REMAINING_HEADER));
        assertNotNull(result.chain.getRequest(), "sorğu növbəti filtrə ötürülməlidir");
    }

    @Test
    void rejectedRequestGets429ProblemDetailsAndStopsTheChain() throws Exception {
        send("POST", LOGIN, "1.1.1.1");
        send("POST", LOGIN, "1.1.1.1");
        Result result = send("POST", LOGIN, "1.1.1.1");

        assertEquals(429, result.response.getStatus());
        assertNull(result.chain.getRequest(), "rədd olunan sorğu heç bir sonrakı filtrə çatmamalıdır");
        assertEquals("application/problem+json", result.response.getContentType().split(";")[0]);
        assertEquals("60", result.response.getHeader("Retry-After"));
        assertEquals("0", result.response.getHeader(RateLimitFilter.REMAINING_HEADER));

        JsonNode body = json.readTree(result.response.getContentAsString());
        assertEquals(429, body.get("status").asInt());
        assertEquals("Too Many Requests", body.get("title").asString());
        assertEquals(60, body.get("retryAfterSeconds").asLong());
        assertEquals(CONTEXT + LOGIN, body.get("instance").asString());
    }

    @Test
    void unlimitedRequestHasNoRateLimitHeader() throws Exception {
        Result result = send("GET", "/index.html", "1.1.1.1");
        assertEquals(200, result.response.getStatus());
        assertNull(result.response.getHeader(RateLimitFilter.REMAINING_HEADER));
    }

    @Test
    void ipv6AddressesFromSameNetworkShareOneLimit() throws Exception {
        send("POST", LOGIN, "2001:db8:1:2::1");
        send("POST", LOGIN, "2001:db8:1:2::2");
        assertEquals(429, send("POST", LOGIN, "2001:db8:1:2::3").response.getStatus(), "eyni /64 — eyni limit");
        assertEquals(200, send("POST", LOGIN, "2001:db8:1:3::1").response.getStatus(), "başqa /64 — ayrı limit");
    }

    @Test
    void clientKeyGroupsIpv6BySlash64AndKeepsIpv4() {
        assertEquals("203.0.113.7", RateLimitFilter.clientKey("203.0.113.7"));
        assertEquals("2001:db8:1:2::/64", RateLimitFilter.clientKey("2001:db8:1:2::5"));
        assertEquals("2001:db8:1:2::/64", RateLimitFilter.clientKey("2001:db8:1:2:ffff:ffff:ffff:ffff"));
        assertNotEquals(RateLimitFilter.clientKey("2001:db8:1:2::5"), RateLimitFilter.clientKey("2001:db8:1:3::5"));
        assertEquals("203.0.113.7", RateLimitFilter.clientKey("::ffff:203.0.113.7"), "IPv4-mapped IPv6 = IPv4");
        assertEquals("unknown", RateLimitFilter.clientKey(null));
        assertEquals("unknown", RateLimitFilter.clientKey(" "));
    }

    private record Result(MockHttpServletResponse response, MockFilterChain chain) {
    }

    private Result send(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, CONTEXT + path);
        request.setContextPath(CONTEXT);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(response, chain);
    }
}
