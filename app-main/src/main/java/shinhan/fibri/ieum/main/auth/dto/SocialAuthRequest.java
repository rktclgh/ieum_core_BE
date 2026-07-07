package shinhan.fibri.ieum.main.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SocialAuthRequest(
	@NotBlank
	@Pattern(regexp = "^(google|kakao)$")
	String provider,

	String idToken,

	String code,

	String nonce
) {

	@AssertTrue
	public boolean isGoogleIdTokenPresent() {
		return !"google".equals(provider) || hasText(idToken);
	}

	@AssertTrue
	public boolean isKakaoCodePresent() {
		return !"kakao".equals(provider) || hasText(code);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
