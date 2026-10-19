package com.example.robotpathplanning.security.captcha;

import java.net.URI;
import java.util.List;

/**
 * Dəstəklənən CAPTCHA provayderləri və onların fərqləri. Yoxlama məntiqi hamısı üçün eynidir
 * (SiteVerifyCaptchaVerifier): hər ikisi "siteverify" API-si ilə işləyir və eyni formatda cavab verir.
 *
 *   RECAPTCHA - Google reCAPTCHA v2 "Mən robot deyiləm": istifadəçi HƏMİŞƏ klik edir,
 *               şübhəli halda şəkil tapşırığı da verilir
 *   TURNSTILE - Cloudflare Turnstile: çox vaxt klik olmadan keçir, yalnız şübhəli halda klik istəyir
 */
public enum CaptchaProvider {

    RECAPTCHA(URI.create("https://www.google.com/recaptcha/api/siteverify"),
            List.of("https://www.google.com/recaptcha/", "https://www.gstatic.com/recaptcha/"),
            List.of("https://www.google.com/recaptcha/", "https://recaptcha.google.com/recaptcha/")),

    TURNSTILE(URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"),
            List.of("https://challenges.cloudflare.com"),
            List.of("https://challenges.cloudflare.com"));

    /** Google test açarlarının cavabında həmişə bu hostname olur. */
    private static final String RECAPTCHA_TEST_HOSTNAME = "testkey.google.com";

    private final URI verifyUrl;
    private final List<String> scriptSources;
    private final List<String> frameSources;

    CaptchaProvider(URI verifyUrl, List<String> scriptSources, List<String> frameSources) {
        this.verifyUrl = verifyUrl;
        this.scriptSources = scriptSources;
        this.frameSources = frameSources;
    }

    public URI verifyUrl() {
        return verifyUrl;
    }

    /** CSP script-src: vidcetin skripti buradan yüklənir. */
    public List<String> scriptSources() {
        return scriptSources;
    }

    /** CSP frame-src: vidcet bu mənbədən iframe kimi açılır. */
    public List<String> frameSources() {
        return frameSources;
    }

    /** Cavab provayderin "hamını buraxan" test açarı ilə alınıbmı. */
    boolean isTestKeyResult(String hostname, boolean testingKeyFlag) {
        return switch (this) {
            case TURNSTILE -> testingKeyFlag;
            case RECAPTCHA -> RECAPTCHA_TEST_HOSTNAME.equals(hostname);
        };
    }
}
