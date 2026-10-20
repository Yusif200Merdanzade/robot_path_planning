package com.example.robotpathplanning.security.captcha;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Set;

/**
 * CAPTCHA tokenini server tərəfdə provayderin "siteverify" API-si ilə yoxlayır.
 * Google reCAPTCHA və Cloudflare Turnstile eyni API-ni verir — fərqləri CaptchaProvider-dədir.
 *
 * Brauzerin "keçdim" deməsinə güvənilmir: hücumçu brauzeri atlayıb API-ni birbaşa çağıra bilər.
 * Token yalnız provayder təsdiqləyəndə qəbul olunur. Token birdəfəlikdir və bir neçə dəqiqə yaşayır —
 * bunu provayder özü yoxlayır ("timeout-or-duplicate").
 *
 * Əlavə yoxlamalar (provayder "success" desə belə):
 *   test key - prod-da test açarının nəticəsi rədd olunur: təsadüfən test açarı qoyulsa,
 *              CAPTCHA hamını səssizcə buraxardı
 *   action   - token məhz login vidcetindən alınıb (yalnız Turnstile göndərir)
 *   hostname - token bizim saytda alınıb (app.captcha.expected-hostname verilibsə)
 */
public class SiteVerifyCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(SiteVerifyCaptchaVerifier.class);

    /** Turnstile vidcetində verilən action ilə eyni olmalıdır (bax: login.html). */
    public static final String LOGIN_ACTION = "login";
    /** Token bundan uzun olmur — daha uzunu provayderə göndərmədən rədd olunur. */
    private static final int MAX_TOKEN_LENGTH = 4096;
    /** Bizim konfiqurasiya səhvi (istifadəçinin günahı deyil): gizli açar yoxdur / səhvdir. */
    private static final Set<String> CONFIGURATION_ERRORS = Set.of("missing-input-secret", "invalid-input-secret");

    private final CaptchaProperties properties;
    private final RestClient restClient;

    public SiteVerifyCaptchaVerifier(CaptchaProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public Result verify(String token, String clientIp) {
        // Boş token provayderə heç göndərilmir: Google-un test açarı boş tokeni də "keçir" sayır
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) return Result.REJECTED;

        SiteVerifyResponse response;
        try {
            response = restClient.post()
                    .uri(properties.provider().verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form(token, clientIp))
                    .retrieve()
                    .body(SiteVerifyResponse.class);
        } catch (RestClientException e) {
            // Provayder cavab vermədi / 5xx / timeout — fail-closed: giriş rədd olunur
            log.error("CAPTCHA yoxlanması alınmadı: {} əlçatan deyil ({})", properties.provider(), e.getMessage());
            return Result.UNAVAILABLE;
        }
        return evaluate(response);
    }

    private Result evaluate(SiteVerifyResponse response) {
        if (response == null) return Result.UNAVAILABLE;
        List<String> errors = response.errorCodes() == null ? List.of() : response.errorCodes();

        if (!response.success()) {
            if (errors.stream().anyMatch(CONFIGURATION_ERRORS::contains) || errors.contains("internal-error")) {
                log.error("CAPTCHA yoxlanması alınmadı: {} xətası {} (gizli açarı yoxlayın)", properties.provider(), errors);
                return Result.UNAVAILABLE;
            }
            log.debug("CAPTCHA rədd olundu: {}", errors);
            return Result.REJECTED;
        }
        if (properties.provider().isTestKeyResult(response.hostname(), response.usedTestingKeyFlag())
                && !properties.allowTestKeys()) {
            log.error("CAPTCHA test açarı ilə işləyir, prod-da qəbul olunmur: CAPTCHA_SITE_KEY / CAPTCHA_SECRET_KEY yoxlayın");
            return Result.UNAVAILABLE;
        }
        if (response.action() != null && !LOGIN_ACTION.equals(response.action())) {
            log.warn("CAPTCHA tokeni başqa vidcetdən gəlib: action={}", response.action());
            return Result.REJECTED;
        }
        if (properties.expectedHostname() != null && !properties.expectedHostname().equals(response.hostname())) {
            log.warn("CAPTCHA tokeni başqa saytda alınıb: hostname={}", response.hostname());
            return Result.REJECTED;
        }
        return Result.PASSED;
    }

    private MultiValueMap<String, String> form(String token, String clientIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.secretKey());
        form.add("response", token);
        if (clientIp != null) form.add("remoteip", clientIp);
        return form;
    }

    /** siteverify cavabının bizə lazım olan hissəsi (hər iki provayder üçün eyni format). */
    record SiteVerifyResponse(boolean success,
                              @JsonProperty("error-codes") List<String> errorCodes,
                              String hostname,
                              String action,
                              Metadata metadata) {

        boolean usedTestingKeyFlag() {
            return metadata != null && Boolean.TRUE.equals(metadata.resultWithTestingKey());
        }

        // Boolean (boolean yox): real açarın cavabında bu sahə olmur (metadata-da yalnız ephemeral_id),
        // Jackson 3 isə olmayan sahəni primitiv boolean-a yaza bilmir -> cavab oxunmurdu
        record Metadata(@JsonProperty("result_with_testing_key") Boolean resultWithTestingKey) {
        }
    }
}
