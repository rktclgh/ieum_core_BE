package shinhan.fibri.ieum.main.auth.service;

public interface EmailVerificationRateLimiter {

	boolean tryConsumeSignupSend(String email);
}
