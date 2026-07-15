package shinhan.fibri.ieum.common.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import shinhan.fibri.ieum.common.auth.domain.GenderType;
import shinhan.fibri.ieum.common.auth.domain.User;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;

@DataJpaTest
class UserAuthStateRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void returnsExactCanonicalStateAndExcludesSoftDeletedUser() {
		User active = User.createEmailUser(
			"canonical@example.com",
			"hash",
			"canonical",
			LocalDate.of(1990, 1, 1),
			GenderType.female,
			"KR"
		);
		active.changeRole(UserRole.admin);
		active.suspend();
		active = userRepository.save(active);

		User deleted = User.createEmailUser(
			"deleted-canonical@example.com",
			"hash",
			"deleted-canonical",
			LocalDate.of(1991, 1, 1),
			GenderType.male,
			"US"
		);
		deleted.markDeleted(OffsetDateTime.parse("2026-07-15T00:00:00Z"));
		deleted = userRepository.saveAndFlush(deleted);
		entityManager.clear();

		assertThat(userRepository.findAuthStateById(active.getId()))
			.contains(new UserAuthState(
				"canonical@example.com",
				UserRole.admin,
				UserStatus.suspended,
				2L
			));
		assertThat(userRepository.findAuthStateById(deleted.getId())).isEmpty();
	}

	@SpringBootApplication(scanBasePackages = "shinhan.fibri.ieum.common")
	@EntityScan(basePackageClasses = User.class)
	static class TestApplication {
	}
}
