package shinhan.fibri.ieum.main.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailVerificationCodeStoreTest {

	@Test
	void saveSignupCodeStoresHashWithSignupKeyAndTtl() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		RedisEmailVerificationCodeStore store = new RedisEmailVerificationCodeStore(redisTemplate);

		store.saveSignupCode("user@example.com", "hashed-code", Duration.ofSeconds(180));

		verify(valueOperations).set(
			"auth:email:signup:code:user@example.com",
			"hashed-code",
			Duration.ofSeconds(180)
		);
	}
}
