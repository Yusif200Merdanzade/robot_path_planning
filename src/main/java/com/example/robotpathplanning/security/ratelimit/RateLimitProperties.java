package com.example.robotpathplanning.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sorğu limitləri (app.rate-limit.*, bax: application.properties). Başlanğıcda yoxlanır:
 * səhv qayda deploy anında görünsün, hücum anında yox.
 *
 * @param enabled            false olsa limit tamamilə söndürülür (qəza halı üçün açar)
 * @param maxTrackedClients  yaddaşda eyni anda saxlanan vedrə sayının yuxarı həddi
 * @param rules              qaydalar; sorğuya uyğun gələn BÜTÜN qaydalar tətbiq olunur
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled,
                                  @DefaultValue("100000") int maxTrackedClients,
                                  List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (maxTrackedClients <= 0) throw new IllegalStateException("app.rate-limit.max-tracked-clients > 0 olmalıdır");
        Set<String> names = new HashSet<>();
        for (Rule rule : rules) {
            if (!names.add(rule.name())) throw new IllegalStateException("app.rate-limit: təkrarlanan qayda adı " + rule.name());
        }
    }

    /**
     * @param name   qaydanın adı (loglarda və vedrə açarında görünür), məs. login
     * @param method HTTP metodu (GET, POST); boşdursa hamısı
     * @param path   tətbiq daxilindəki yol və ya şablon (context-path olmadan), məs. /api/auth/login, /api/**
     * @param limits bir və ya bir neçə limit, hamısı eyni anda qüvvədədir (məs. dəqiqədə 5 VƏ saatda 20)
     */
    public record Rule(String name, String method, String path, List<Limit> limits) {

        public Rule {
            if (name == null || name.isBlank()) throw new IllegalStateException("app.rate-limit: qaydanın adı (name) boşdur");
            if (path == null || !path.startsWith("/")) throw new IllegalStateException("app.rate-limit " + name + ": path '/' ilə başlamalıdır");
            if (limits == null || limits.isEmpty()) throw new IllegalStateException("app.rate-limit " + name + ": ən azı bir limit lazımdır");
            limits = List.copyOf(limits);
        }
    }

    /**
     * @param capacity period ərzində icazə verilən sorğu sayı (eyni zamanda ani partlayışın həddi)
     * @param period   limitin vaxt pəncərəsi, məs. 1m, 1h
     */
    public record Limit(int capacity, Duration period) {

        public Limit {
            if (capacity <= 0) throw new IllegalStateException("app.rate-limit: capacity > 0 olmalıdır");
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalStateException("app.rate-limit: period müsbət olmalıdır");
            }
        }
    }
}
