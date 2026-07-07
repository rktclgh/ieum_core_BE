package shinhan.fibri.ieum.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import shinhan.fibri.ieum.main.auth.service.GoogleSocialIdentityVerifier;

@Configuration
public class SocialAuthConfig {

	@Bean
	JwtDecoder googleSocialJwtDecoder(
		@Value("${app.auth.social.google.jwk-set-uri}") String jwkSetUri
	) {
		return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
	}

	@Bean
	GoogleSocialIdentityVerifier googleSocialIdentityVerifier(
		@Qualifier("googleSocialJwtDecoder") JwtDecoder googleSocialJwtDecoder,
		@Value("${app.auth.social.google.client-id}") String clientId
	) {
		return new GoogleSocialIdentityVerifier(googleSocialJwtDecoder, clientId);
	}
}
