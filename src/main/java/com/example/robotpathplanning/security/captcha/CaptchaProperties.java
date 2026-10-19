package com.example.robotpathplanning.security.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * CAPTCHA parametrləri (app.captcha.*, bax: application.properties). Başlanğıcda yoxlanır.
 *
 * @param enabled          false olsa login CAPTCHA-sız işləyir (qəza halı üçün açar)
 * @param siteKey          açıq açar: brauzerdəki vidcet üçün, səhifəyə verilir
 * @param secretKey        gizli açar: yalnız server Cloudflare-ə tokeni yoxlatdıranda istifadə edir
 * @param verifyUrl        Cloudflare-in yoxlama ünvanı
 * @param timeout          Cloudflare cavab verməsə nə qədər gözlənilir (sonra giriş rədd olunur)
 * @param expectedHostname boş deyilsə, token yalnız bu saytda alınıbsa qəbul olunur
 * @param allowTestKeys    Cloudflare-in test açarlarının nəticəsi qəbul olunsunmu (yalnız lokal üçün true)
 */
@ConfigurationProperties("app.captcha")
public record CaptchaProperties(@DefaultValue("true") boolean enabled,
                                String siteKey,
                                String secretKey,
                                @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") URI verifyUrl,
                                @DefaultValue("5s") Duration timeout,
                                String expectedHostname,
                                @DefaultValue("false") boolean allowTestKeys) {

    public CaptchaProperties {
        if (enabled) {
            requireValue("app.captcha.site-key (TURNSTILE_SITE_KEY)", siteKey);
            requireValue("app.captcha.secret-key (TURNSTILE_SECRET_KEY)", secretKey);
        }
        if (expectedHostname != null && expectedHostname.isBlank()) expectedHostname = null;
    }

    /** Boş dəyər və ya açılmamış "${...}" (mühit dəyişəni verilməyib) qəbul olunmur. */
    private static void requireValue(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " təyin olunmayıb (və ya CAPTCHA-nı söndürün: app.captcha.enabled=false)");
        }
    }
}
