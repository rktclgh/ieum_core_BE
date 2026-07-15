package shinhan.fibri.ieum.main.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuthSessionRotationIntegrationTest {

	private static final int REDIS_PORT = 6379;
	private static final Duration SESSION_TTL = Duration.ofDays(7);

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(
		DockerImageName.parse("redis:7.4-alpine")
	).withExposedPorts(REDIS_PORT);

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisAuthSessionStore store;

	@BeforeEach
	void setUp() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
			REDIS.getHost(),
			REDIS.getMappedPort(REDIS_PORT)
		);
		connectionFactory = new LettuceConnectionFactory(configuration);
		connectionFactory.afterPropertiesSet();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		Set<String> keys = redisTemplate.keys("*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
		store = new RedisAuthSessionStore(redisTemplate, new AuthSessionProperties(
			false,
			"Lax",
			"",
			1_800,
			SESSION_TTL.toSeconds()
		));
	}

	@AfterEach
	void tearDown() {
		connectionFactory.destroy();
	}

	@Test
	void twoConcurrentRotationsHaveOneWinnerAndOnePreviousReuseResult() throws Exception {
		AuthSession session = session("current-hash", "superseded-hash");
		store.create(session);
		redisTemplate.opsForValue().set(
			"auth:refresh:superseded-hash",
			"sid-1",
			SESSION_TTL
		);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<RotationAttempt> first = executor.submit(() -> rotateWhenReleased(
				session,
				"new-hash-a",
				ready,
				start
			));
			Future<RotationAttempt> second = executor.submit(() -> rotateWhenReleased(
				session,
				"new-hash-b",
				ready,
				start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			RotationAttempt firstAttempt = first.get(5, TimeUnit.SECONDS);
			RotationAttempt secondAttempt = second.get(5, TimeUnit.SECONDS);
			assertThat(List.of(firstAttempt.result(), secondAttempt.result()))
				.containsExactlyInAnyOrder(
					RefreshTokenRotationResult.ROTATED,
					RefreshTokenRotationResult.PREVIOUS
				);

			String winnerHash = firstAttempt.result() == RefreshTokenRotationResult.ROTATED
				? firstAttempt.newHash()
				: secondAttempt.newHash();
			String loserHash = firstAttempt.result() == RefreshTokenRotationResult.PREVIOUS
				? firstAttempt.newHash()
				: secondAttempt.newHash();
			AuthSession rotated = store.findBySessionId("sid-1").orElseThrow();
			assertThat(rotated.refreshTokenHash()).isEqualTo(winnerHash);
			assertThat(rotated.prevRefreshTokenHash()).isEqualTo("current-hash");
			assertThat(redisTemplate.opsForValue().get("auth:refresh:current-hash"))
				.isEqualTo("sid-1");
			assertThat(redisTemplate.opsForValue().get("auth:refresh:" + winnerHash))
				.isEqualTo("sid-1");
			assertThat(redisTemplate.opsForValue().get("auth:refresh:" + loserHash)).isNull();
			assertThat(redisTemplate.opsForValue().get("auth:refresh:superseded-hash")).isNull();
			assertThat(redisTemplate.getExpire("auth:session:sid-1")).isPositive();
			assertThat(redisTemplate.getExpire("auth:refresh:current-hash")).isPositive();
			assertThat(redisTemplate.getExpire("auth:refresh:" + winnerHash)).isPositive();
			assertThat(redisTemplate.getExpire("auth:user:42:sessions")).isPositive();
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void mismatchDoesNotMutateSessionOrRefreshIndexes() {
		AuthSession session = session("current-hash", null);
		store.create(session);
		Map<Object, Object> sessionBefore = redisTemplate.opsForHash().entries("auth:session:sid-1");

		RefreshTokenRotationResult result = store.compareAndRotateRefreshToken(
			session,
			"wrong-hash",
			"new-hash"
		);

		assertThat(result).isEqualTo(RefreshTokenRotationResult.MISMATCH);
		assertThat(redisTemplate.opsForHash().entries("auth:session:sid-1"))
			.isEqualTo(sessionBefore);
		assertThat(redisTemplate.opsForValue().get("auth:refresh:current-hash"))
			.isEqualTo("sid-1");
		assertThat(redisTemplate.opsForValue().get("auth:refresh:new-hash")).isNull();
		assertThat(redisTemplate.opsForValue().get("auth:refresh:wrong-hash")).isNull();
	}

	@Test
	void previousHashCannotBecomeCurrentAgainAndDoesNotMutateState() {
		AuthSession session = session("current-hash", "previous-hash");
		store.create(session);
		Map<Object, Object> sessionBefore = redisTemplate.opsForHash().entries("auth:session:sid-1");

		RefreshTokenRotationResult result = store.compareAndRotateRefreshToken(
			session,
			"current-hash",
			"previous-hash"
		);

		assertThat(result).isEqualTo(RefreshTokenRotationResult.MISMATCH);
		assertThat(redisTemplate.opsForHash().entries("auth:session:sid-1"))
			.isEqualTo(sessionBefore);
		assertThat(redisTemplate.opsForValue().get("auth:refresh:current-hash"))
			.isEqualTo("sid-1");
		assertThat(redisTemplate.opsForValue().get("auth:refresh:previous-hash")).isNull();
	}

	@Test
	void existingNewHashIndexOwnedBySameSessionIsACollisionAndDoesNotMutateState() {
		AuthSession session = session("current-hash", null);
		store.create(session);
		redisTemplate.opsForValue().set("auth:refresh:new-hash", "sid-1", SESSION_TTL);
		Map<Object, Object> sessionBefore = redisTemplate.opsForHash().entries("auth:session:sid-1");

		RefreshTokenRotationResult result = store.compareAndRotateRefreshToken(
			session,
			"current-hash",
			"new-hash"
		);

		assertThat(result).isEqualTo(RefreshTokenRotationResult.MISMATCH);
		assertThat(redisTemplate.opsForHash().entries("auth:session:sid-1"))
			.isEqualTo(sessionBefore);
		assertThat(redisTemplate.opsForValue().get("auth:refresh:current-hash"))
			.isEqualTo("sid-1");
		assertThat(redisTemplate.opsForValue().get("auth:refresh:new-hash"))
			.isEqualTo("sid-1");
	}

	@Test
	void scriptValidationFailureDoesNotPartiallyMutateState() {
		AuthSession session = session("current-hash", null);
		store.create(session);
		redisTemplate.delete("auth:user:42:sessions");
		redisTemplate.opsForValue().set("auth:user:42:sessions", "corrupt", SESSION_TTL);
		Map<Object, Object> sessionBefore = redisTemplate.opsForHash().entries("auth:session:sid-1");

		assertThatThrownBy(() -> store.compareAndRotateRefreshToken(
			session,
			"current-hash",
			"new-hash"
		)).isInstanceOf(RuntimeException.class);

		assertThat(redisTemplate.opsForHash().entries("auth:session:sid-1"))
			.isEqualTo(sessionBefore);
		assertThat(redisTemplate.opsForValue().get("auth:refresh:current-hash"))
			.isEqualTo("sid-1");
		assertThat(redisTemplate.opsForValue().get("auth:refresh:new-hash")).isNull();
		assertThat(redisTemplate.opsForValue().get("auth:user:42:sessions"))
			.isEqualTo("corrupt");
	}

	private RotationAttempt rotateWhenReleased(
		AuthSession session,
		String newHash,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("concurrent rotation start timed out");
		}
		return new RotationAttempt(
			newHash,
			store.compareAndRotateRefreshToken(session, "current-hash", newHash)
		);
	}

	private AuthSession session(String refreshHash, String previousHash) {
		return new AuthSession(
			"sid-1",
			42L,
			"admin@example.com",
			refreshHash,
			previousHash,
			UserRole.admin,
			UserStatus.active,
			OffsetDateTime.parse("2026-07-03T00:00:00Z"),
			7L
		);
	}

	private record RotationAttempt(String newHash, RefreshTokenRotationResult result) {
	}
}
