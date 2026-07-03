package shinhan.fibri.ieum.main.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisEmailVerificationRateLimiter implements EmailVerificationRateLimiter {

	private static final String SIGNUP_SEND_COUNT_KEY_PREFIX = "auth:email:signup:send-count:";
	private static final int MINUTE_LIMIT = 1;
	private static final int DAILY_LIMIT = 10;
	private static final Duration MINUTE_TTL = Duration.ofMinutes(1);
	private static final Duration DAILY_TTL = Duration.ofDays(1);

	private final StringRedisTemplate redisTemplate;

	@Override
	public boolean tryConsumeSignupSend(String email) {
		long minuteCount = incrementWithExpiry(minuteKey(email), MINUTE_TTL);
		if (minuteCount > MINUTE_LIMIT) {
			return false;
		}

		long dailyCount = incrementWithExpiry(dayKey(email), DAILY_TTL);
		return dailyCount <= DAILY_LIMIT;
	}

	private long incrementWithExpiry(String key, Duration ttl) {
		Long count = redisTemplate.opsForValue().increment(key);
		if (Long.valueOf(1L).equals(count)) {
			redisTemplate.expire(key, ttl);
		}
		return count == null ? 0L : count;
	}

	private String minuteKey(String email) {
		return SIGNUP_SEND_COUNT_KEY_PREFIX + "minute:" + email;
	}

	private String dayKey(String email) {
		return SIGNUP_SEND_COUNT_KEY_PREFIX + "day:" + email;
	}
}
