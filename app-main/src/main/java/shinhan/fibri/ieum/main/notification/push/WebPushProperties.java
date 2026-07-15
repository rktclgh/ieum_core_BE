package shinhan.fibri.ieum.main.notification.push;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record WebPushProperties(
	boolean enabled,
	String vapidPublicKey,
	Set<String> allowedEndpointHosts
) {

	public WebPushProperties(boolean enabled, String vapidPublicKey, String allowedEndpointHosts) {
		this(enabled, vapidPublicKey, parseAllowedHosts(allowedEndpointHosts));
	}

	public WebPushProperties {
		vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey;
		allowedEndpointHosts = Set.copyOf(allowedEndpointHosts == null ? Set.of() : allowedEndpointHosts);
		if (!enabled) {
			vapidPublicKey = "";
		}
		else if (vapidPublicKey.isBlank() || !vapidPublicKey.equals(vapidPublicKey.trim())) {
			throw new IllegalStateException("app.web-push.vapid-public-key must be configured");
		}
		else if (allowedEndpointHosts.isEmpty()) {
			throw new IllegalStateException("app.web-push.allowed-endpoint-hosts must be configured");
		}
	}

	private static Set<String> parseAllowedHosts(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(host -> !host.isBlank())
			.map(host -> host.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}
}
