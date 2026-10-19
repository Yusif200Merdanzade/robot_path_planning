package com.example.robotpathplanning.security.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String START = "/api/start";
    private static final String IP = "1.1.1.1";

    /** Əl ilə idarə olunan saat: blok və doldurmanı real gözləmədən yoxlamaq üçün. */
    private static final class ManualClock implements TimeMeter {
        long nanos = 1_000_000_000L;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }

    private final ManualClock clock = new ManualClock();

    private RateLimiter limiter(RateLimitProperties.Rule... rules) {
        return new RateLimiter(new RateLimitProperties(true, 1000, List.of(rules)),
                RateLimiter.inMemoryBuckets(1000, clock), clock);
    }

    private static RateLimitProperties.Rule rule(String name, String method, String path, RateLimitProperties.Limit... limits) {
        return new RateLimitProperties.Rule(name, method, path, List.of(limits));
    }

    private static RateLimitProperties.Limit limit(int capacity, Duration period) {
        return new RateLimitProperties.Limit(capacity, period);
    }

    @Test
    void allowsCapacityThenBlocksForFullPeriod() {
        RateLimiter limiter = limiter(rule("start", null, START, limit(4, Duration.ofSeconds(5))));

        for (long left = 3; left >= 0; left--) {
            RateLimiter.Decision decision = limiter.tryConsume("GET", START, IP);
            assertTrue(decision.allowed());
            assertEquals(left, decision.remaining().getAsLong());
        }
        RateLimiter.Decision rejected = limiter.tryConsume("GET", START, IP);
        assertFalse(rejected.allowed());
        assertEquals("start", rejected.rule());
        assertEquals(5, rejected.retryAfterSeconds());

        clock.advance(Duration.ofSeconds(2));
        assertEquals(3, limiter.tryConsume("GET", START, IP).retryAfterSeconds(), "blok davam edir, 3 san qalıb");
    }

    @Test
    void blockLastsFullPeriodFromRejectionNotFromWindowStart() {
        RateLimiter limiter = limiter(rule("login", "POST", LOGIN, limit(5, Duration.ofSeconds(5))));

        limiter.tryConsume("POST", LOGIN, IP);                       // pəncərə burada başlayır
        clock.advance(Duration.ofMillis(4500));                      // pəncərənin sonuna 0.5 san qalıb
        for (int i = 0; i < 4; i++) limiter.tryConsume("POST", LOGIN, IP);

        RateLimiter.Decision rejected = limiter.tryConsume("POST", LOGIN, IP);
        assertFalse(rejected.allowed());
        assertEquals(5, rejected.retryAfterSeconds(), "0.5 san yox, düz 5 san blok");
    }

    @Test
    void afterBlockExactlyCapacityAgainInEveryRound() {
        RateLimiter limiter = limiter(rule("start", null, START, limit(4, Duration.ofSeconds(5))));

        for (int round = 1; round <= 3; round++) {
            for (int i = 1; i <= 4; i++) {
                assertTrue(limiter.tryConsume("GET", START, IP).allowed(), "raund " + round + ", basış " + i);
                clock.advance(Duration.ofMillis(400));
            }
            assertFalse(limiter.tryConsume("GET", START, IP).allowed(), "raund " + round + ": 5-ci basış bloklanmalıdır");
            clock.advance(Duration.ofSeconds(5));
        }
    }

    @Test
    void shortBlockDoesNotResetLongTermLimit() {
        RateLimiter limiter = limiter(rule("login", "POST", LOGIN,
                limit(5, Duration.ofSeconds(5)), limit(20, Duration.ofHours(1))));

        // Hücumçu 5 cəhd edir, 5 san blok gözləyir, təkrarlayır: 4 raundda 20 cəhd
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 5; i++) assertTrue(limiter.tryConsume("POST", LOGIN, IP).allowed());
            assertFalse(limiter.tryConsume("POST", LOGIN, IP).allowed());
            clock.advance(Duration.ofSeconds(5));
        }
        RateLimiter.Decision rejected = limiter.tryConsume("POST", LOGIN, IP);
        assertFalse(rejected.allowed(), "saatlıq limit (20) bitib — 5 san bloklar onu sıfırlamamalıdır");
        assertEquals(3600, rejected.retryAfterSeconds());
    }

    @Test
    void everyMatchingRuleIsApplied() {
        RateLimiter limiter = limiter(
                rule("login", "POST", LOGIN, limit(5, Duration.ofMinutes(1))),
                rule("api", null, "/api/**", limit(7, Duration.ofMinutes(1))));

        for (int i = 0; i < 5; i++) assertTrue(limiter.tryConsume("POST", LOGIN, IP).allowed());
        assertEquals("login", limiter.tryConsume("POST", LOGIN, IP).rule());

        // Ümumi "api" qaydasından 5 jeton artıq gedib — başqa API ünvanına 2 sorğu qalıb
        assertTrue(limiter.tryConsume("GET", START, IP).allowed());
        assertTrue(limiter.tryConsume("GET", START, IP).allowed());
        assertEquals("api", limiter.tryConsume("GET", START, IP).rule());
    }

    @Test
    void clientsHaveIndependentLimits() {
        RateLimiter limiter = limiter(rule("login", "POST", LOGIN, limit(1, Duration.ofMinutes(1))));
        assertTrue(limiter.tryConsume("POST", LOGIN, "1.1.1.1").allowed());
        assertFalse(limiter.tryConsume("POST", LOGIN, "1.1.1.1").allowed());
        assertTrue(limiter.tryConsume("POST", LOGIN, "2.2.2.2").allowed());
    }

    @Test
    void requestsWithoutMatchingRuleAreNotLimited() {
        RateLimiter limiter = limiter(rule("login", "POST", LOGIN, limit(1, Duration.ofMinutes(1))));
        for (int i = 0; i < 10; i++) {
            RateLimiter.Decision page = limiter.tryConsume("GET", "/index.html", IP);
            assertTrue(page.allowed());
            assertTrue(page.remaining().isEmpty(), "limitsiz sorğuda X-RateLimit-Remaining olmamalıdır");
            assertTrue(limiter.tryConsume("GET", LOGIN, IP).allowed(), "metod uyğun gəlmir");
        }
    }
}
