package shinhan.fibri.ieum.ai.internal.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class InternalHmacReplayCache {

	enum MarkResult {
		MARKED,
		REPLAYED,
		CAPACITY_EXCEEDED
	}

	private final Clock clock;
	private final int maxEntries;
	private final long ttlSeconds;
	private final Map<String, Long> expiresAtByKey = new HashMap<>();
	private final ArrayDeque<String> insertionOrder = new ArrayDeque<>();

	InternalHmacReplayCache(Clock clock, int maxEntries, long ttlSeconds) {
		this.clock = clock;
		this.maxEntries = maxEntries;
		this.ttlSeconds = ttlSeconds;
	}

	synchronized MarkResult markIfAbsent(String key) {
		pruneExpired();
		if (expiresAtByKey.containsKey(key)) {
			return MarkResult.REPLAYED;
		}
		if (expiresAtByKey.size() >= maxEntries) {
			return MarkResult.CAPACITY_EXCEEDED;
		}
		expiresAtByKey.put(key, clock.instant().getEpochSecond() + ttlSeconds);
		insertionOrder.addLast(key);
		return MarkResult.MARKED;
	}

	private void pruneExpired() {
		long now = clock.instant().getEpochSecond();
		while (!insertionOrder.isEmpty()) {
			String oldest = insertionOrder.peekFirst();
			Long expiresAt = expiresAtByKey.get(oldest);
			if (expiresAt == null) {
				insertionOrder.pollFirst();
			}
			else if (expiresAt <= now) {
				insertionOrder.pollFirst();
				expiresAtByKey.remove(oldest);
			}
			else {
				return;
			}
		}
	}

}
