package shinhan.fibri.ieum.main.auth.service;

import org.junit.jupiter.api.Test;
import shinhan.fibri.ieum.main.auth.dto.SendEmailVerificationRequest;
import shinhan.fibri.ieum.main.auth.dto.SendEmailVerificationResponse;
import shinhan.fibri.ieum.main.auth.dto.VerifyEmailVerificationRequest;
import shinhan.fibri.ieum.main.auth.dto.VerifyEmailVerificationResponse;
import shinhan.fibri.ieum.main.auth.exception.InvalidEmailVerificationCodeException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTest {

	@Test
	void sendSignupCodeNormalizesEmailStoresHashedCodeAndSendsMail() {
		EmailVerificationCodeStore codeStore = mock(EmailVerificationCodeStore.class);
		VerificationMailSender mailSender = mock(VerificationMailSender.class);
		VerificationCodeGenerator codeGenerator = mock(VerificationCodeGenerator.class);
		VerificationCodeHasher codeHasher = mock(VerificationCodeHasher.class);
		EmailVerificationTokenGenerator tokenGenerator = mock(EmailVerificationTokenGenerator.class);
		EmailVerificationService service = new EmailVerificationService(
			codeStore,
			mailSender,
			codeGenerator,
			codeHasher,
			tokenGenerator
		);
		when(codeGenerator.generate()).thenReturn("123456");
		when(codeHasher.hash("123456")).thenReturn("hashed-code");

		SendEmailVerificationResponse response = service.sendSignupCode(
			new SendEmailVerificationRequest(" USER@example.COM ")
		);

		assertThat(response.expiresInSeconds()).isEqualTo(180);
		verify(codeStore).saveSignupCode("user@example.com", "hashed-code", Duration.ofSeconds(180));
		verify(mailSender).sendSignupCode("user@example.com", "123456", 180);
	}

	@Test
	void verifySignupCodeNormalizesEmailDeletesCodeAndStoresVerificationToken() {
		EmailVerificationCodeStore codeStore = mock(EmailVerificationCodeStore.class);
		VerificationMailSender mailSender = mock(VerificationMailSender.class);
		VerificationCodeGenerator codeGenerator = mock(VerificationCodeGenerator.class);
		VerificationCodeHasher codeHasher = mock(VerificationCodeHasher.class);
		EmailVerificationTokenGenerator tokenGenerator = mock(EmailVerificationTokenGenerator.class);
		EmailVerificationService service = new EmailVerificationService(
			codeStore,
			mailSender,
			codeGenerator,
			codeHasher,
			tokenGenerator
		);
		when(codeStore.findSignupCodeHash("user@example.com")).thenReturn(Optional.of("hashed-code"));
		when(codeHasher.hash("123456")).thenReturn("hashed-code");
		when(tokenGenerator.generate()).thenReturn("verification-token");

		VerifyEmailVerificationResponse response = service.verifySignupCode(
			new VerifyEmailVerificationRequest(" USER@example.COM ", "123456")
		);

		assertThat(response.emailVerificationToken()).isEqualTo("verification-token");
		assertThat(response.expiresInSeconds()).isEqualTo(1800);
		verify(codeStore).deleteSignupCode("user@example.com");
		verify(codeStore).saveSignupVerificationToken(
			"verification-token",
			"user@example.com",
			Duration.ofSeconds(1800)
		);
	}

	@Test
	void verifySignupCodeThrowsWhenCodeIsExpired() {
		EmailVerificationCodeStore codeStore = mock(EmailVerificationCodeStore.class);
		VerificationMailSender mailSender = mock(VerificationMailSender.class);
		VerificationCodeGenerator codeGenerator = mock(VerificationCodeGenerator.class);
		VerificationCodeHasher codeHasher = mock(VerificationCodeHasher.class);
		EmailVerificationTokenGenerator tokenGenerator = mock(EmailVerificationTokenGenerator.class);
		EmailVerificationService service = new EmailVerificationService(
			codeStore,
			mailSender,
			codeGenerator,
			codeHasher,
			tokenGenerator
		);
		when(codeStore.findSignupCodeHash("user@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.verifySignupCode(
			new VerifyEmailVerificationRequest(" USER@example.COM ", "123456")
		)).isInstanceOf(InvalidEmailVerificationCodeException.class);

		verify(codeStore, never()).deleteSignupCode("user@example.com");
		verify(codeStore, never()).saveSignupVerificationToken(
			"verification-token",
			"user@example.com",
			Duration.ofSeconds(1800)
		);
	}

	@Test
	void verifySignupCodeThrowsWhenCodeDoesNotMatch() {
		EmailVerificationCodeStore codeStore = mock(EmailVerificationCodeStore.class);
		VerificationMailSender mailSender = mock(VerificationMailSender.class);
		VerificationCodeGenerator codeGenerator = mock(VerificationCodeGenerator.class);
		VerificationCodeHasher codeHasher = mock(VerificationCodeHasher.class);
		EmailVerificationTokenGenerator tokenGenerator = mock(EmailVerificationTokenGenerator.class);
		EmailVerificationService service = new EmailVerificationService(
			codeStore,
			mailSender,
			codeGenerator,
			codeHasher,
			tokenGenerator
		);
		when(codeStore.findSignupCodeHash("user@example.com")).thenReturn(Optional.of("saved-code-hash"));
		when(codeHasher.hash("000000")).thenReturn("request-code-hash");

		assertThatThrownBy(() -> service.verifySignupCode(
			new VerifyEmailVerificationRequest(" USER@example.COM ", "000000")
		)).isInstanceOf(InvalidEmailVerificationCodeException.class);

		verify(codeStore, never()).deleteSignupCode("user@example.com");
		verify(codeStore, never()).saveSignupVerificationToken(
			"verification-token",
			"user@example.com",
			Duration.ofSeconds(1800)
		);
	}
}
