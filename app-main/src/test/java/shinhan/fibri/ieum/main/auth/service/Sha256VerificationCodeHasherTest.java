package shinhan.fibri.ieum.main.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256VerificationCodeHasherTest {

	@Test
	void hashReturnsStableNonPlainTextHash() {
		VerificationCodeHasher hasher = new Sha256VerificationCodeHasher();

		String hash = hasher.hash("user@example.com", "123456");

		assertThat(hash).isEqualTo(hasher.hash("user@example.com", "123456"));
		assertThat(hash).isNotEqualTo("123456");
		assertThat(hash).hasSize(64);
	}

	@Test
	void hashUsesEmailContextForSameCode() {
		VerificationCodeHasher hasher = new Sha256VerificationCodeHasher();

		String userHash = hasher.hash("user@example.com", "123456");
		String otherUserHash = hasher.hash("other@example.com", "123456");

		assertThat(userHash).isNotEqualTo(otherUserHash);
	}
}
