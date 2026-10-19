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
 * Cloudflare Turnstile tokenini server tərəfdə yoxlayır (siteverify API).
 *
 * Brauzerin "keçdim" deməsinə güvənilmir: hücumçu brauzeri atlayıb API-ni birbaşa çağıra bilər.
 * Token yalnız Cloudflare təsdiqləyəndə qəbul olunur. Token birdəfəlikdir və 5 dəqiqə yaşayır —
 * bunu Cloudflare özü yoxlayır ("timeout-or-duplicate").
 *
 * Əlavə yoxlamalar (Cloudflare "success" desə belə):
 *   action   - token məhz login vidcetindən alınıb (Cloudflare göndərəndə; test açarları göndərmir)
 *   hostname - token bizim saytda alınıb (app.captcha.expected-hostname verilibsə)
 *   test key - prod-da test açarının nəticəsi rədd olunur: təsadüfən test açarı qoyulsa,
 *              CAPTCHA hamını səssizcə buraxardı
 */
public class TurnstileVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    /** Vidcetdə verilən action ilə eyni olmalıdır (bax: login.html). */
    public static final String LOGIN_ACTION = "login";
    /** Cloudflare: token ən çox 2048 simvol olur — daha uzunu Cloudflare-ə göndərmədən rədd olunur. */
    private static final int MAX_TOKEN_LENGTH = 2048;
    /** Bizim konfiqurasiya səhvi (istifadəçinin günahı deyil): gizli açar yoxdur / səhvdir. */
    private static final Set<String> CONFIGURATION_ERRORS = Set.of("missing-input-secret", "invalid-input-secret");

    private final CaptchaProperties properties;
    private final RestClient restClient;

    public TurnstileVerifier(CaptchaProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public Result verify(String token, String clientIp) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) return Result.REJECTED;

        SiteVerifyResponse response;
        try {
            response = restClient.post()
                    .uri(properties.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form(token, clientIp))
                    .retrieve()
                    .body(SiteVerifyResponse.class);
        } catch (RestClientException e) {
            // Cloudflare cavab vermədi / 5xx / timeout — fail-closed: giriş rədd olunur
            log.error("CAPTCHA yoxlanması alınmadı: Cloudflare əlçatan deyil ({})", e.getMessage());
            return Result.UNAVAILABLE;
        }
        return evaluate(response);
    }

    private Result evaluate(SiteVerifyResponse response) {
        if (response == null) return Result.UNAVAILABLE;
        List<String> errors = response.errorCodes() == null ? List.of() : response.errorCodes();

        if (!response.success()) {
            if (errors.stream().anyMatch(CONFIGURATION_ERRORS::contains) || errors.contains("internal-error")) {
                log.error("CAPTCHA yoxlanması alınmadı: Cloudflare xətası {} (gizli açarı yoxlayın)", errors);
                return Result.UNAVAILABLE;
            }
            log.debug("CAPTCHA rədd olundu: {}", errors);
            return Result.REJECTED;
        }
        if (response.usedTestKey() && !properties.allowTestKeys()) {
            log.error("CAPTCHA test açarı ilə işləyir, prod-da qəbul olunmur: TURNSTILE_* açarlarını yoxlayın");
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

    /** Cloudflare siteverify cavabının bizə lazım olan hissəsi. */
    record SiteVerifyResponse(boolean success,
                              @JsonProperty("error-codes") List<String> errorCodes,
                              String hostname,
                              String action,
                              Metadata metadata) {

        boolean usedTestKey() {
            return metadata != null && metadata.resultWithTestingKey();
        }

        record Metadata(@JsonProperty("result_with_testing_key") boolean resultWithTestingKey) {
        }
    }
}
