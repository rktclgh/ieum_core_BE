package shinhan.fibri.ieum.main.auth.service;

import org.junit.jupiter.api.Test;
import shinhan.fibri.ieum.main.auth.dto.SendEmailVerificationRequest;
import shinhan.fibri.ieum.main.auth.dto.SendEmailVerificationResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
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
		EmailVerificationService service = new EmailVerificationService(
			codeStore,
			mailSender,
			codeGenerator,
			codeHasher
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
}
