package shinhan.fibri.ieum.main.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

	@Test
	void hashReturnsNonPlainTextBCryptHash() {
		PasswordHasher passwordHasher = new BCryptPasswordHasher();

		String hash = passwordHasher.hash("password123");

		assertThat(hash).isNotEqualTo("password123");
		assertThat(hash).startsWith("$2");
	}
}
