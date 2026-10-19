package com.example.robotpathplanning.security.captcha;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * CAPTCHA provayderini seçir. Başqa provayderə (reCAPTCHA, hCaptcha) keçid yalnız bu metodu dəyişir.
 */
@Configuration
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaConfig {

    @Bean
    public CaptchaVerifier captchaVerifier(CaptchaProperties properties) {
        if (!properties.enabled()) {
            LoggerFactory.getLogger(CaptchaConfig.class).warn("CAPTCHA söndürülüb (app.captcha.enabled=false) — login yalnız parol və rate limit ilə qorunur");
            return (token, clientIp) -> CaptchaVerifier.Result.PASSED;
        }
        // Timeout məcburidir: Cloudflare ilişsə, login sorğuları da sonsuz gözləməsin
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        return new TurnstileVerifier(properties, RestClient.builder().requestFactory(requestFactory).build());
    }
}
