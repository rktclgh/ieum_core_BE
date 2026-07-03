package shinhan.fibri.ieum.main.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisEmailVerificationCodeStore implements EmailVerificationCodeStore {

	private static final String SIGNUP_CODE_KEY_PREFIX = "auth:email:signup:code:";

	private final StringRedisTemplate redisTemplate;

	@Override
	public void saveSignupCode(String email, String codeHash, Duration ttl) {
		redisTemplate.opsForValue().set(SIGNUP_CODE_KEY_PREFIX + email, codeHash, ttl);
	}
}
