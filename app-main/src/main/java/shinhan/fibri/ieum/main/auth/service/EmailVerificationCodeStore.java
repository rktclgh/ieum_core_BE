package shinhan.fibri.ieum.main.auth.service;

import java.time.Duration;

public interface EmailVerificationCodeStore {

	void saveSignupCode(String email, String codeHash, Duration ttl);
}
