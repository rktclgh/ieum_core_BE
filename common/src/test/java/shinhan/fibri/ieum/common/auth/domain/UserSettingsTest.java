package shinhan.fibri.ieum.common.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserSettingsTest {

	@Test
	void defaultForInitializesSettingsDefaults() {
		User user = User.createEmailUser(
			"user@example.com",
			"password-hash",
			"nickname",
			LocalDate.of(2000, 1, 1)
		);

		UserSettings settings = UserSettings.defaultFor(user);

		assertThat(settings.getUser()).isEqualTo(user);
		assertThat(settings.getLanguage()).isEqualTo("ko");
		assertThat(settings.isCameraPermission()).isFalse();
		assertThat(settings.isPushPermission()).isTrue();
		assertThat(settings.isNotifyAll()).isTrue();
		assertThat(settings.isNotifyMeeting()).isTrue();
		assertThat(settings.isNotifyQuestion()).isTrue();
		assertThat(settings.getNotifyRadiusKm()).isEqualTo(5);
	}
}
