package shinhan.fibri.ieum.main.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WebPushPropertiesTest {

	@Test
	void disabledModeNormalizesPublicKeyAndAllowsEmptyHosts() {
		WebPushProperties properties = new WebPushProperties(false, "ignored-secret", "");

		assertThat(properties.enabled()).isFalse();
		assertThat(properties.vapidPublicKey()).isEmpty();
		assertThat(properties.allowedEndpointHosts()).isEmpty();
	}

	@Test
	void enabledModeNormalizesAllowedHosts() {
		WebPushProperties properties = new WebPushProperties(
			true,
			"public-key",
			" FCM.GOOGLEAPIS.COM, push.services.mozilla.com "
		);

		assertThat(properties.allowedEndpointHosts())
			.isEqualTo(Set.of("fcm.googleapis.com", "push.services.mozilla.com"));
	}

	@Test
	void enabledModeRequiresPublicKeyAndHostAllowList() {
		assertThatThrownBy(() -> new WebPushProperties(true, " ", "fcm.googleapis.com"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new WebPushProperties(true, "public-key", " "))
			.isInstanceOf(IllegalStateException.class);
	}
}
