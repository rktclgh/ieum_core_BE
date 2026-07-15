package shinhan.fibri.ieum.common.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class UserAuthVersionTest {

	@Test
	void advancesOnlyWhenCanonicalAuthorizationStateChanges() {
		User user = User.createEmailUser(
			"auth-version@example.com",
			"hash",
			"auth-version",
			LocalDate.of(1995, 5, 20),
			GenderType.female,
			"KR"
		);

		assertThat(user.getAuthVersion()).isZero();

		user.suspend();
		assertThat(user.getAuthVersion()).isEqualTo(1);
		user.suspend();
		assertThat(user.getAuthVersion()).isEqualTo(1);

		user.activate();
		assertThat(user.getAuthVersion()).isEqualTo(2);
		user.activate();
		assertThat(user.getAuthVersion()).isEqualTo(2);

		user.changeRole(UserRole.admin);
		assertThat(user.getAuthVersion()).isEqualTo(3);
		user.changeRole(UserRole.admin);
		assertThat(user.getAuthVersion()).isEqualTo(3);

		OffsetDateTime deletedAt = OffsetDateTime.parse("2026-07-15T00:00:00Z");
		user.markDeleted(deletedAt);
		assertThat(user.getAuthVersion()).isEqualTo(4);
		user.markDeleted(deletedAt.plusDays(1));
		assertThat(user.getAuthVersion()).isEqualTo(4);
		assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
	}
}
