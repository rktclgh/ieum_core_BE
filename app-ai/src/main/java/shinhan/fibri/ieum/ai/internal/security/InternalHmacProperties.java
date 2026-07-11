package shinhan.fibri.ieum.ai.internal.security;

import java.util.Base64;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ieum.internal.hmac")
public class InternalHmacProperties implements InitializingBean {

	private static final int MIN_SECRET_BYTES = 32;
	private static final long MAX_CLOCK_SKEW_SECONDS = 60;
	private static final int MAX_BODY_BYTES = 1_048_576;

	private String serviceName;
	private String currentKeyId;
	private String currentSecretBase64;
	private String previousKeyId;
	private String previousSecretBase64;
	private long clockSkewSeconds = 60;
	private int maxBodyBytes = 1_048_576;
	private int replayMaxEntries = 10_000;
	private long replayTtlSeconds = 120;

	@Override
	public void afterPropertiesSet() {
		requireHeaderSafe(serviceName, "service-name");
		requireHeaderSafe(currentKeyId, "current-key-id");
		requireNonBlank(currentSecretBase64, "current-secret-base64");
		decodeSecret(currentSecretBase64, "current-secret-base64");

		boolean hasPreviousKey = hasText(previousKeyId);
		boolean hasPreviousSecret = hasText(previousSecretBase64);
		if (hasPreviousKey != hasPreviousSecret) {
			throw new IllegalStateException("previous-key-id and previous-secret-base64 must be configured together");
		}
		if (hasPreviousSecret) {
			requireHeaderSafe(previousKeyId, "previous-key-id");
			decodeSecret(previousSecretBase64, "previous-secret-base64");
		}
		if (clockSkewSeconds < 0 || clockSkewSeconds > MAX_CLOCK_SKEW_SECONDS) {
			throw new IllegalStateException("clock-skew-seconds must be between 0 and " + MAX_CLOCK_SKEW_SECONDS);
		}
		if (maxBodyBytes <= 0 || maxBodyBytes > MAX_BODY_BYTES) {
			throw new IllegalStateException("max-body-bytes must be between 1 and " + MAX_BODY_BYTES);
		}
		if (replayMaxEntries <= 0) {
			throw new IllegalStateException("replay-max-entries must be > 0");
		}
		long minimumReplayTtlSeconds = Math.max(1, clockSkewSeconds * 2);
		if (replayTtlSeconds < minimumReplayTtlSeconds) {
			throw new IllegalStateException("replay-ttl-seconds must be at least " + minimumReplayTtlSeconds);
		}
	}

	byte[] currentSecret() {
		return decodeSecret(currentSecretBase64, "current-secret-base64");
	}

	byte[] previousSecret() {
		if (!hasText(previousSecretBase64)) {
			return null;
		}
		return decodeSecret(previousSecretBase64, "previous-secret-base64");
	}

	private byte[] decodeSecret(String value, String propertyName) {
		try {
			byte[] decoded = Base64.getDecoder().decode(value);
			if (decoded.length < MIN_SECRET_BYTES) {
				throw new IllegalStateException(propertyName + " must be at least " + MIN_SECRET_BYTES + " bytes");
			}
			return decoded;
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(propertyName + " must be valid Base64", ex);
		}
	}

	private void requireNonBlank(String value, String propertyName) {
		if (!hasText(value)) {
			throw new IllegalStateException(propertyName + " must be configured");
		}
	}

	private void requireHeaderSafe(String value, String propertyName) {
		requireNonBlank(value, propertyName);
		for (int i = 0; i < value.length(); i++) {
			if (Character.isISOControl(value.charAt(i))) {
				throw new IllegalStateException(propertyName + " must not contain control characters");
			}
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getCurrentKeyId() {
		return currentKeyId;
	}

	public void setCurrentKeyId(String currentKeyId) {
		this.currentKeyId = currentKeyId;
	}

	public String getCurrentSecretBase64() {
		return currentSecretBase64;
	}

	public void setCurrentSecretBase64(String currentSecretBase64) {
		this.currentSecretBase64 = currentSecretBase64;
	}

	public String getPreviousKeyId() {
		return previousKeyId;
	}

	public void setPreviousKeyId(String previousKeyId) {
		this.previousKeyId = previousKeyId;
	}

	public String getPreviousSecretBase64() {
		return previousSecretBase64;
	}

	public void setPreviousSecretBase64(String previousSecretBase64) {
		this.previousSecretBase64 = previousSecretBase64;
	}

	public long getClockSkewSeconds() {
		return clockSkewSeconds;
	}

	public void setClockSkewSeconds(long clockSkewSeconds) {
		this.clockSkewSeconds = clockSkewSeconds;
	}

	public int getMaxBodyBytes() {
		return maxBodyBytes;
	}

	public void setMaxBodyBytes(int maxBodyBytes) {
		this.maxBodyBytes = maxBodyBytes;
	}

	public int getReplayMaxEntries() {
		return replayMaxEntries;
	}

	public void setReplayMaxEntries(int replayMaxEntries) {
		this.replayMaxEntries = replayMaxEntries;
	}

	public long getReplayTtlSeconds() {
		return replayTtlSeconds;
	}

	public void setReplayTtlSeconds(long replayTtlSeconds) {
		this.replayTtlSeconds = replayTtlSeconds;
	}

}
