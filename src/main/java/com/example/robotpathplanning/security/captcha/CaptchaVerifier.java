package com.example.robotpathplanning.security.captcha;

/**
 * "Bu CAPTCHA tokeni həqiqidirmi?" — provayderdən asılı olmayan müqavilə.
 *
 * İndi SiteVerifyCaptchaVerifier işləyir: Google reCAPTCHA və Cloudflare Turnstile (bax: CaptchaProvider).
 * Fərqli API-li provayderə keçid yalnız bu interfeysin yeni implementasiyasını tələb edir —
 * AuthService və login axını dəyişmir.
 */
public interface CaptchaVerifier {

    /**
     * @param token    brauzerdəki vidcetin verdiyi birdəfəlik token
     * @param clientIp istifadəçinin IP-si (provayder risk qiymətləndirməsi üçün)
     */
    Result verify(String token, String clientIp);

    enum Result {
        /** İnsan təsdiqləndi. */
        PASSED,
        /** Token yoxdur, səhvdir, vaxtı keçib və ya artıq istifadə olunub — giriş rədd olunur. */
        REJECTED,
        /** Provayder cavab vermədi. Giriş yenə rədd olunur (fail-closed), amma istifadəçiyə səbəb fərqlidir. */
        UNAVAILABLE
    }
}
