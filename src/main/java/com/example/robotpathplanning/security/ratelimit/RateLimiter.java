package com.example.robotpathplanning.security.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

/**
 * Sorğu limitinin qərarı: "bu klient bu sorğunu indi edə bilərmi?". HTTP-dən asılı deyil.
 *
 * Alqoritm token bucket-dir (Bucket4j): hər (qayda, klient) cütü üçün bir vedrə; hər sorğu bir jeton
 * aparır, jetonlar zamanla bərabər sürətlə yenidən dolur. Qaydada bir neçə limit ola bilər
 * (məs. dəqiqədə 5 VƏ saatda 20): qısa partlayışı da, uzunmüddətli yavaş hücumu da tutur.
 *
 * Sorğuya uyğun gələn bütün qaydalar ardıcıl yoxlanır (məs. həm "login", həm ümumi "api" qaydası).
 * Biri rədd edən kimi dayanılır; əvvəlki qaydalardan götürülmüş jeton geri qaytarılmır —
 * bu, limiti yalnız sərtləşdirir, heç vaxt yumşaltmır.
 *
 * Vedrələr ProxyManager-də saxlanır: indi proses yaddaşında (inMemoryBuckets) — Render-də bir server
 * işlədiyi üçün kifayətdir. Bir neçə server olanda limit hamısı üçün ortaq olmalıdır: o zaman
 * bucket4j_jdk17-lettuce ilə Redis ProxyManager-i ötürmək kifayətdir, bu sinfin məntiqi dəyişmir.
 */
public class RateLimiter {

    /** Vedrə tam dolandan bu qədər sonra yaddaşdan silinir — tam vedrəni silmək limiti dəyişmir. */
    private static final Duration KEEP_AFTER_REFILL = Duration.ofSeconds(10);

    private final List<CompiledRule> rules;
    private final ProxyManager<String> buckets;

    public RateLimiter(RateLimitProperties properties, ProxyManager<String> buckets) {
        PathPatternParser parser = new PathPatternParser();
        this.rules = properties.rules().stream().map(rule -> new CompiledRule(rule, parser)).toList();
        this.buckets = buckets;
    }

    /**
     * Yaddaşda vedrələr. maximumSize yaddaşın yuxarı həddidir: hücum zamanı milyonlarla IP gəlsə belə
     * yaddaş dolmur (ən köhnə vedrələr atılır — bu, həmin IP-lər üçün limiti sıfırlayır, yaddaşın
     * tükənməsindən isə yaxşıdır). Saat parametrdir ki, testlərdə vaxtı əl ilə irəli çəkmək olsun.
     */
    public static ProxyManager<String> inMemoryBuckets(long maxSize, TimeMeter clock) {
        return Bucket4jCaffeine.<String>builderFor(Caffeine.newBuilder().maximumSize(maxSize))
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_AFTER_REFILL))
                .clientClock(clock)
                .build();
    }

    // ============================================================================================
    //  ƏSAS HİSSƏ — rate limiting-in bütün məntiqi buradadır. Qalan siniflər yalnız bunu çağırır:
    //    RateLimitFilter     - HTTP sorğusunu bu metoda ötürür, cavabı 200 / 429-a çevirir
    //    RateLimitProperties - qaydaları application.properties-dən oxuyur
    // ============================================================================================
    /**
     * Sorğuya uyğun gələn hər qayda üçün klientin vedrəsindən bir jeton götürməyə çalışır.
     *
     * @param method    HTTP metodu
     * @param path      tətbiq daxilindəki yol (context-path olmadan)
     * @param clientKey klientin açarı (IP)
     * @return icazə (qalan jetonla) və ya rədd (hansı qayda, nə qədər gözləmək lazımdır)
     */
    public Decision tryConsume(String method, String path, String clientKey) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        long minRemaining = Long.MAX_VALUE;
        boolean matched = false;

        for (CompiledRule rule : rules) {
            if (!rule.matches(method, pathContainer)) continue;
            matched = true;
            ConsumptionProbe probe = buckets.builder()
                    .build(rule.name + "|" + clientKey, () -> rule.bucketConfiguration)
                    .tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                return Decision.rejected(rule.name, Duration.ofNanos(probe.getNanosToWaitForRefill()));
            }
            minRemaining = Math.min(minRemaining, probe.getRemainingTokens());
        }
        return matched ? Decision.allowed(minRemaining) : Decision.notLimited();
    }

    /**
     * @param allowed    sorğu icra oluna bilərmi
     * @param rule       rədd edən qaydanın adı (rədd olunmayıbsa null)
     * @param retryAfter növbəti jetona qədər gözləmə (rədd olunmayıbsa sıfır)
     * @param remaining  uyğun qaydalarda qalan ən az jeton (heç bir qayda uyğun deyilsə boş)
     */
    public record Decision(boolean allowed, String rule, Duration retryAfter, OptionalLong remaining) {

        static Decision allowed(long remaining) {
            return new Decision(true, null, Duration.ZERO, OptionalLong.of(remaining));
        }

        static Decision notLimited() {
            return new Decision(true, null, Duration.ZERO, OptionalLong.empty());
        }

        static Decision rejected(String rule, Duration retryAfter) {
            return new Decision(false, rule, retryAfter, OptionalLong.of(0));
        }

        /** Retry-After başlığı üçün: yuxarı yuvarlaqlaşdırılmış, ən azı 1 saniyə. */
        public long retryAfterSeconds() {
            long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
            return Math.max(1, seconds);
        }
    }

    /** Qayda başlanğıcda bir dəfə hazırlanır: yol şablonu parse olunur, vedrə konfiqurasiyası qurulur. */
    private static final class CompiledRule {

        final String name;
        final String method;
        final PathPattern pattern;
        final BucketConfiguration bucketConfiguration;

        CompiledRule(RateLimitProperties.Rule rule, PathPatternParser parser) {
            this.name = rule.name();
            this.method = rule.method();
            this.pattern = parser.parse(rule.path());
            var builder = BucketConfiguration.builder();
            for (RateLimitProperties.Limit limit : rule.limits()) {
                // greedy: jetonlar period boyu bərabər paylanaraq qayıdır (5/dəq → hər 12 san bir jeton),
                // pəncərə sərhədində ikiqat partlayışa imkan vermir
                builder.addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()));
            }
            this.bucketConfiguration = builder.build();
        }

        boolean matches(String requestMethod, PathContainer path) {
            return (method == null || method.equalsIgnoreCase(requestMethod)) && pattern.matches(path);
        }
    }
}
