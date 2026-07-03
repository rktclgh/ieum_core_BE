package shinhan.fibri.ieum.main.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailVerificationRateLimiterTest {

	@Test
	void tryConsumeSignupSendAllowsFirstRequestAndSetsMinuteAndDailyExpiry() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("auth:email:signup:send-count:minute:user@example.com"))
			.thenReturn(1L);
		when(valueOperations.increment("auth:email:signup:send-count:day:user@example.com"))
			.thenReturn(1L);
		RedisEmailVerificationRateLimiter rateLimiter = new RedisEmailVerificationRateLimiter(redisTemplate);

		assertThat(rateLimiter.tryConsumeSignupSend("user@example.com")).isTrue();

		verify(redisTemplate).expire(
			"auth:email:signup:send-count:minute:user@example.com",
			Duration.ofMinutes(1)
		);
		verify(redisTemplate).expire(
			"auth:email:signup:send-count:day:user@example.com",
			Duration.ofDays(1)
		);
	}

	@Test
	void tryConsumeSignupSendRejectsSecondRequestInSameMinute() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("auth:email:signup:send-count:minute:user@example.com"))
			.thenReturn(2L);
		RedisEmailVerificationRateLimiter rateLimiter = new RedisEmailVerificationRateLimiter(redisTemplate);

		assertThat(rateLimiter.tryConsumeSignupSend("user@example.com")).isFalse();

		verify(valueOperations, never()).increment("auth:email:signup:send-count:day:user@example.com");
	}

	@Test
	void tryConsumeSignupSendRejectsEleventhRequestInSameDay() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("auth:email:signup:send-count:minute:user@example.com"))
			.thenReturn(1L);
		when(valueOperations.increment("auth:email:signup:send-count:day:user@example.com"))
			.thenReturn(11L);
		RedisEmailVerificationRateLimiter rateLimiter = new RedisEmailVerificationRateLimiter(redisTemplate);

		assertThat(rateLimiter.tryConsumeSignupSend("user@example.com")).isFalse();
	}
}
