package com.example.robotpathplanning.security.captcha;

/** CAPTCHA keçilmədi və ya yoxlana bilmədi — giriş parola baxılmadan dayandırılır. */
public class CaptchaException extends RuntimeException {

    private final CaptchaVerifier.Result result;

    public CaptchaException(CaptchaVerifier.Result result) {
        super(result == CaptchaVerifier.Result.UNAVAILABLE
                ? "Robot yoxlaması hazırda əlçatan deyil. Bir az sonra yenidən cəhd edin."
                : "Robot yoxlaması keçilmədi. Yenidən cəhd edin.");
        this.result = result;
    }

    public CaptchaVerifier.Result result() {
        return result;
    }
}
