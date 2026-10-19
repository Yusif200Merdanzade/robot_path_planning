package com.example.robotpathplanning.security.captcha;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileVerifierTest {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private MockRestServiceServer server;

    private TurnstileVerifier verifier(boolean allowTestKeys, String expectedHostname) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        CaptchaProperties properties = new CaptchaProperties(true, "site-key", "secret-key", URI.create(VERIFY_URL),
                Duration.ofSeconds(5), expectedHostname, allowTestKeys);
        return new TurnstileVerifier(properties, builder.build());
    }

    private void cloudflareReplies(String json) {
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void passesWhenCloudflareConfirms() {
        TurnstileVerifier verifier = verifier(false, "robot.example.com");
        server.expect(requestTo(VERIFY_URL))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "secret", "secret-key", "response", "token-123", "remoteip", "1.2.3.4")))
                .andRespond(withSuccess("""
                        {"success":true,"error-codes":[],"hostname":"robot.example.com","action":"login"}""",
                        MediaType.APPLICATION_JSON));

        assertEquals(CaptchaVerifier.Result.PASSED, verifier.verify("token-123", "1.2.3.4"));
        server.verify();
    }

    @Test
    void rejectsInvalidOrReusedToken() {
        TurnstileVerifier verifier = verifier(false, null);
        cloudflareReplies("""
                {"success":false,"error-codes":["timeout-or-duplicate"]}""");
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("used-token", "1.2.3.4"));
    }

    @Test
    void rejectsMissingOrOversizedTokenWithoutCallingCloudflare() {
        TurnstileVerifier verifier = verifier(false, null);
        server.expect(never(), requestTo(VERIFY_URL));

        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify(null, "1.2.3.4"));
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify(" ", "1.2.3.4"));
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("x".repeat(2049), "1.2.3.4"));
        server.verify();
    }

    @Test
    void rejectsTokenFromAnotherWidgetOrSite() {
        TurnstileVerifier verifier = verifier(false, "robot.example.com");
        cloudflareReplies("""
                {"success":true,"hostname":"robot.example.com","action":"signup"}""");
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("t", "1.2.3.4"), "başqa action");

        verifier = verifier(false, "robot.example.com");
        cloudflareReplies("""
                {"success":true,"hostname":"evil.example.com","action":"login"}""");
        assertEquals(CaptchaVerifier.Result.REJECTED, verifier.verify("t", "1.2.3.4"), "başqa hostname");
    }

    @Test
    void testKeyResultIsRefusedUnlessAllowed() {
        String testKeyReply = """
                {"success":true,"hostname":"example.com","metadata":{"result_with_testing_key":true}}""";

        TurnstileVerifier prod = verifier(false, null);
        cloudflareReplies(testKeyReply);
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, prod.verify("t", "1.2.3.4"), "prod-da test açarı qəbul olunmur");

        TurnstileVerifier local = verifier(true, null);
        cloudflareReplies(testKeyReply);
        assertEquals(CaptchaVerifier.Result.PASSED, local.verify("t", "1.2.3.4"));
    }

    @Test
    void failsClosedWhenCloudflareIsDownOrMisconfigured() {
        TurnstileVerifier verifier = verifier(false, null);
        server.expect(requestTo(VERIFY_URL)).andRespond(withServerError());
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, verifier.verify("t", "1.2.3.4"), "5xx");

        verifier = verifier(false, null);
        server.expect(requestTo(VERIFY_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, verifier.verify("t", "1.2.3.4"), "502");

        verifier = verifier(false, null);
        cloudflareReplies("""
                {"success":false,"error-codes":["invalid-input-secret"]}""");
        assertEquals(CaptchaVerifier.Result.UNAVAILABLE, verifier.verify("t", "1.2.3.4"), "səhv gizli açar — bizim xəta");
    }
}
