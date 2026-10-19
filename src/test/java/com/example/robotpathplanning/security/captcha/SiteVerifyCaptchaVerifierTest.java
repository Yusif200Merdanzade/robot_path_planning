package com.example.robotpathplanning.security.captcha;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SiteVerifyCaptchaVerifierTest {

    private MockRestServiceServer server;

    private SiteVerifyCaptchaVerifier verifier(CaptchaProvider provider, boolean allowTestKeys, String expectedHostname) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        CaptchaProperties properties = new CaptchaProperties(true, provider, "site-key", "secret-key",
                Duration.ofSeconds(5), expectedHostname, allowTestKeys);
        return new SiteVerifyCaptchaVerifier(properties, builder.build());
    }

    private void providerReplies(CaptchaProvider provider, String json) {
        server.expect(requestTo(provider.verifyUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void recaptchaPassesWhenGoogleConfirms() {
        SiteVerifyCaptchaVerifier verifier = verifier(CaptchaProvider.RECAPTCHA, false, "robot.example.com");
        server.expect(requestTo(CaptchaProvider.RECAPTCHA.verifyUrl()))
                .andExpect(content().formDataContains(Map.of(
                        "secret", "secret-key", "response", "token-123", "remoteip", "1.2.3.4")))
                .andRespond(withSuccess("""
                        {"success":true,"challenge_ts":"2026-10-19T13:28:23Z","hostname":"robot.example.com"}""",
                        MediaType.APPLICATION_JSON));

        assertEquals(CaptchaVerifier.Result.PASSED, verifier.verify("token-123", "1.2.3.4"));
        server.verify();
    }

    @Test
    void turnstilePassesWhenCloudflareConfirms() {
        SiteVerifyCaptchaVerifier verifier = verifier(CaptchaProvider.TURNSTILE, false, "robot.example.com");
        providerReplies(CaptchaProvider.TURNSTILE, """
                {"success":true,"error-codes":[],"hostname":"robot.example.com","action":"login"}""");
        assertEquals(CaptchaVerifier.Result.PASSED, verifier.verify("token-123", "1.2.3.4"));
    }

    @Test
    void rejectsInvalidOrReusedToken() {
        SiteVerifyCaptchaVerifier verifier = verifier(CaptchaProvider.RECAPTCHA, false, null);
        providerReplies(CaptchaProvider.RECAPTCHA, """
                {"success":false,"error-codes":["timeout-or-duplicate"]}""");
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("used-token", "1.2.3.4"));
    }

    @Test
    void rejectsMissingOrOversizedTokenWithoutCallingProvider() {
        // Google-un test açarı boş tokeni də "keçir" sayır — ona görə provayderə heç göndərilməməlidir
        SiteVerifyCaptchaVerifier verifier = verifier(CaptchaProvider.RECAPTCHA, true, null);
        server.expect(never(), anything());

        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify(null, "1.2.3.4"));
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify(" ", "1.2.3.4"));
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("x".repeat(4097), "1.2.3.4"));
        server.verify();
    }

    @Test
    void rejectsTokenFromAnotherWidgetOrSite() {
        SiteVerifyCaptchaVerifier verifier = verifier(CaptchaProvider.TURNSTILE, false, "robot.example.com");
        providerReplies(CaptchaProvider.TURNSTILE, """
                {"success":true,"hostname":"robot.example.com","action":"signup"}""");
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("t", "1.2.3.4"), "başqa action");

        verifier = verifier(CaptchaProvider.RECAPTCHA, false, "robot.example.com");
        providerReplies(CaptchaProvider.RECAPTCHA, """
                {"success":true,"hostname":"evil.example.com"}""");
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("t", "1.2.3.4"), "başqa hostname");
    }

    @Test
    void recaptchaTestKeyIsRefusedUnlessAllowed() {
        String testKeyReply = """
                {"success":true,"challenge_ts":"2026-10-19T13:28:23Z","hostname":"testkey.google.com"}""";

        SiteVerifyCaptchaVerifier prod = verifier(CaptchaProvider.RECAPTCHA, false, null);
        providerReplies(CaptchaProvider.RECAPTCHA, testKeyReply);
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, prod.verify("t", "1.2.3.4"), "prod-da test açarı qəbul olunmur");

        SiteVerifyCaptchaVerifier local = verifier(CaptchaProvider.RECAPTCHA, true, null);
        providerReplies(CaptchaProvider.RECAPTCHA, testKeyReply);
        assertEquals(CaptchaVerifier.Result.PASSED, local.verify("t", "1.2.3.4"));
    }

    @Test
    void turnstileTestKeyIsRefusedUnlessAllowed() {
        String testKeyReply = """
                {"success":true,"hostname":"example.com","metadata":{"result_with_testing_key":true}}""";

        SiteVerifyCaptchaVerifier prod = verifier(CaptchaProvider.TURNSTILE, false, null);
        providerReplies(CaptchaProvider.TURNSTILE, testKeyReply);
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, prod.verify("t", "1.2.3.4"));

        SiteVerifyCaptchaVerifier local = verifier(CaptchaProvider.TURNSTILE, true, null);
        providerReplies(CaptchaProvider.TURNSTILE, testKeyReply);
        assertEquals(CaptchaVerifier.Result.PASSED, local.verify("t", "1.2.3.4"));
    }

    @Test
    void failsClosedWhenProviderIsDownOrMisconfigured() {
        SiteVerifyCaptchaVerifier verifier = verifier(CaptchaProvider.RECAPTCHA, false, null);
        server.expect(requestTo(CaptchaProvider.RECAPTCHA.verifyUrl())).andRespond(withServerError());
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, verifier.verify("t", "1.2.3.4"), "5xx");

        verifier = verifier(CaptchaProvider.RECAPTCHA, false, null);
        server.expect(requestTo(CaptchaProvider.RECAPTCHA.verifyUrl())).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, verifier.verify("t", "1.2.3.4"), "502");

        verifier = verifier(CaptchaProvider.RECAPTCHA, false, null);
        providerReplies(CaptchaProvider.RECAPTCHA, """
                {"success":false,"error-codes":["invalid-input-secret"]}""");
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, verifier.verify("t", "1.2.3.4"), "səhv gizli açar — bizim xəta");
    }
}
