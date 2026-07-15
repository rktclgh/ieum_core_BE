package shinhan.fibri.ieum.main.auth.session;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;

@Component
public class RedisAuthSessionStore {
	private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
	private static final DefaultRedisScript<Long> COMPARE_AND_ROTATE_REFRESH_TOKEN =
		new DefaultRedisScript<>("""
			local current = redis.call('HGET', KEYS[1], 'refreshTokenHash')
			local previous = redis.call('HGET', KEYS[1], 'prevRefreshTokenHash')
			if previous and previous == ARGV[1] then
				return 2
			end
			if not current or current ~= ARGV[1] or ARGV[1] == ARGV[2] then
				return 3
			end
			if previous and previous == ARGV[2] then
				return 3
			end
			local indexedSessionId = redis.call('GET', KEYS[2])
			if not indexedSessionId or indexedSessionId ~= ARGV[3] then
				return 3
			end
			local storedUserId = redis.call('HGET', KEYS[1], 'userId')
			if not storedUserId or storedUserId ~= ARGV[4] then
				return 3
			end
			local newTokenOwner = redis.call('GET', KEYS[3])
			if newTokenOwner then
				return 3
			end
			if redis.call('SISMEMBER', KEYS[4], ARGV[3]) ~= 1 then
				return 3
			end

			redis.call('HSET', KEYS[1],
				'prevRefreshTokenHash', current,
				'refreshTokenHash', ARGV[2])
			if previous and previous ~= '' and previous ~= ARGV[1] and previous ~= ARGV[2] then
				redis.call('DEL', ARGV[5] .. previous)
			end
			redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[6])
			redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[6])
			redis.call('EXPIRE', KEYS[1], ARGV[6])
			redis.call('EXPIRE', KEYS[4], ARGV[6])
			return 1
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final AuthSessionProperties authSessionProperties;

	public RedisAuthSessionStore(StringRedisTemplate redisTemplate, AuthSessionProperties authSessionProperties) {
		this.redisTemplate = redisTemplate;
		this.authSessionProperties = authSessionProperties;
	}

	public void create(AuthSession session) {
		redisTemplate.opsForHash().putAll(sessionKey(session.sessionId()), toRedisHash(session));
		redisTemplate.expire(sessionKey(session.sessionId()), sessionTtl());
		redisTemplate.opsForValue().set(refreshKey(session.refreshTokenHash()), session.sessionId(), sessionTtl());
		redisTemplate.opsForSet().add(userSessionsKey(session.userId()), session.sessionId());
		redisTemplate.expire(userSessionsKey(session.userId()), sessionTtl());
	}

	public RefreshTokenRotationResult compareAndRotateRefreshToken(
		AuthSession session,
		String expectedRefreshTokenHash,
		String newRefreshTokenHash
	) {
		// The dynamic superseded-index deletion is intentionally a single-node Redis contract.
		Long result = redisTemplate.execute(
			COMPARE_AND_ROTATE_REFRESH_TOKEN,
			List.of(
				sessionKey(session.sessionId()),
				refreshKey(expectedRefreshTokenHash),
				refreshKey(newRefreshTokenHash),
				userSessionsKey(session.userId())
			),
			expectedRefreshTokenHash,
			newRefreshTokenHash,
			session.sessionId(),
			String.valueOf(session.userId()),
			REFRESH_KEY_PREFIX,
			String.valueOf(sessionTtl().toSeconds())
		);
		if (Long.valueOf(1L).equals(result)) {
			return RefreshTokenRotationResult.ROTATED;
		}
		if (Long.valueOf(2L).equals(result)) {
			return RefreshTokenRotationResult.PREVIOUS;
		}
		if (Long.valueOf(3L).equals(result)) {
			return RefreshTokenRotationResult.MISMATCH;
		}
		throw new IllegalStateException("Unexpected refresh token rotation result: " + result);
	}

	public Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash) {
		String sessionId = redisTemplate.opsForValue().get(refreshKey(refreshTokenHash));
		if (sessionId == null) {
			return Optional.empty();
		}

		return findBySessionId(sessionId);
	}

	public Optional<AuthSession> findBySessionId(String sessionId) {
		Map<Object, Object> session = redisTemplate.opsForHash().entries(sessionKey(sessionId));
		if (session.isEmpty()) {
			return Optional.empty();
		}
		Optional<Long> authVersion = parseAuthVersion(session.get("authVersion"));
		if (authVersion.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new AuthSession(
			sessionId,
			Long.valueOf(session.get("userId").toString()),
			session.get("email").toString(),
			session.get("refreshTokenHash").toString(),
			nullableString(session.get("prevRefreshTokenHash")),
			UserRole.valueOf(session.get("role").toString()),
			UserStatus.valueOf(session.get("status").toString()),
			OffsetDateTime.parse(session.get("createdAt").toString()),
			authVersion.get()
		));
	}

	public void revokeSession(String sessionId) {
		String sessionKey = sessionKey(sessionId);
		Map<Object, Object> session = redisTemplate.opsForHash().entries(sessionKey);
		revokeSession(sessionId, session);
	}

	private void revokeSession(String sessionId, Map<Object, Object> session) {
		String sessionKey = sessionKey(sessionId);
		redisTemplate.delete(sessionKey);
		deleteRefreshKey(session.get("refreshTokenHash"));
		deleteRefreshKey(session.get("prevRefreshTokenHash"));
		Object userId = session.get("userId");
		if (userId != null) {
			redisTemplate.opsForSet().remove(userSessionsKey(userId.toString()), sessionId);
		}
	}

	public void revokeAllSessionsOfUser(Long userId) {
		String userSessionsKey = userSessionsKey(userId);
		Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
		if (sessionIds != null) {
			for (String sessionId : sessionIds) {
				Map<Object, Object> session = redisTemplate.opsForHash().entries(sessionKey(sessionId));
				if (session.isEmpty()) {
					redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
					continue;
				}
				revokeSession(sessionId, session);
			}
		}
		redisTemplate.delete(userSessionsKey);
	}

	private Map<String, String> toRedisHash(AuthSession session) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("userId", String.valueOf(session.userId()));
		values.put("email", session.email());
		values.put("refreshTokenHash", session.refreshTokenHash());
		if (session.prevRefreshTokenHash() != null) {
			values.put("prevRefreshTokenHash", session.prevRefreshTokenHash());
		}
		values.put("role", session.role().name());
		values.put("status", session.status().name());
		values.put("createdAt", session.createdAt().toString());
		values.put("authVersion", String.valueOf(session.authVersion()));
		return values;
	}

	private Optional<Long> parseAuthVersion(Object value) {
		if (value == null || value.toString().isBlank()) {
			return Optional.empty();
		}
		try {
			long authVersion = Long.parseLong(value.toString());
			return authVersion >= 0 ? Optional.of(authVersion) : Optional.empty();
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private Duration sessionTtl() {
		return Duration.ofSeconds(authSessionProperties.refreshTokenMaxAgeSeconds());
	}

	private void deleteRefreshKey(Object refreshTokenHash) {
		if (refreshTokenHash != null) {
			redisTemplate.delete(refreshKey(refreshTokenHash.toString()));
		}
	}

	private String nullableString(Object value) {
		return value == null ? null : value.toString();
	}

	private String sessionKey(String sessionId) {
		return "auth:session:" + sessionId;
	}

	private String refreshKey(String refreshTokenHash) {
		return REFRESH_KEY_PREFIX + refreshTokenHash;
	}

	private String userSessionsKey(Long userId) {
		return userSessionsKey(String.valueOf(userId));
	}

	private String userSessionsKey(String userId) {
		return "auth:user:" + userId + ":sessions";
	}
}
