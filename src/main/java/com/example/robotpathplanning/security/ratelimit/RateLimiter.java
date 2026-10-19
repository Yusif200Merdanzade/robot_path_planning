package com.example.robotpathplanning.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
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
 * aparır, period bitəndə vedrə yenidən tam dolur. Qaydada bir neçə limit ola bilər
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
    private final TimeMeter clock;
    // Bloklanmış klientlər: "qayda|klient" -> blokun bitdiyi an (nanosaniyə). Bax: tryConsume
    private final Cache<String, Long> blockedUntil;

    public RateLimiter(RateLimitProperties properties, ProxyManager<String> buckets, TimeMeter clock) {
        PathPatternParser parser = new PathPatternParser();
        this.rules = properties.rules().stream().map(rule -> new CompiledRule(rule, parser)).toList();
        this.buckets = buckets;
        this.clock = clock;
        this.blockedUntil = Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedClients())
                .expireAfterWrite(rules.stream().map(rule -> rule.minPeriod).max(Duration::compareTo).orElse(Duration.ZERO))
                .build();
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
            String key = rule.name + "|" + clientKey;
            long now = clock.currentTimeNanos();

            // Blok müddəti bitməyibsə jeton belə yoxlanmır
            Long until = blockedUntil.getIfPresent(key);
            if (until != null && until > now) {
                return Decision.rejected(rule.name, Duration.ofNanos(until - now));
            }

            ConsumptionProbe probe = buckets.builder()
                    .build(key, () -> rule.bucketConfiguration)
                    .tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                // Vedrə pəncərəsi ilk cəhddə başlayır, ona görə növbəti doldurmaya qalan vaxt 0..period arası
                // təsadüfi olur (5 san limitdə: 5, 3, 1...). Klient isə blok anından ən azı tam period
                // gözləməlidir. Uzun limit (saatda 20) rədd edibsə, onun gözləməsi onsuz da daha uzundur.
                Duration wait = max(Duration.ofNanos(probe.getNanosToWaitForRefill()), rule.minPeriod);
                blockedUntil.put(key, now + wait.toNanos());
                return Decision.rejected(rule.name, wait);
            }
            minRemaining = Math.min(minRemaining, probe.getRemainingTokens());
        }
        return matched ? Decision.allowed(minRemaining) : Decision.notLimited();
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
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
        final Duration minPeriod;   // blokun minimum müddəti (ən qısa limitin pəncərəsi)

        CompiledRule(RateLimitProperties.Rule rule, PathPatternParser parser) {
            this.name = rule.name();
            this.method = rule.method();
            this.pattern = parser.parse(rule.path());
            var builder = BucketConfiguration.builder();
            for (RateLimitProperties.Limit limit : rule.limits()) {
                // intervally: jetonlar period bitəndə hamısı birdən qayıdır. Limitə düşən klient period
                // bitənə qədər tam gözləyir, sonra bütün cəhdləri geri alır ("47 san sonra yenidən yoxlayın").
                // greedy (hər 12 san bir jeton) bloku "1 cəhd — yenə blok" növbələşməsinə çevirirdi.
                // Qiyməti: pəncərə sərhədində qısa müddətə 2×capacity mümkündür — uzun limit (saatda 20) bunu tutur.
                builder.addLimit(l -> l.capacity(limit.capacity()).refillIntervally(limit.capacity(), limit.period()));
            }
            this.bucketConfiguration = builder.build();
            this.minPeriod = rule.limits().stream().map(RateLimitProperties.Limit::period)
                    .min(Duration::compareTo).orElseThrow();
        }

        boolean matches(String requestMethod, PathContainer path) {
            return (method == null || method.equalsIgnoreCase(requestMethod)) && pattern.matches(path);
        }
    }
}
