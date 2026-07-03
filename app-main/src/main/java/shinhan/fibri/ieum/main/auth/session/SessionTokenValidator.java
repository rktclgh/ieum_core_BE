package shinhan.fibri.ieum.main.auth.session;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;

@Component
@RequiredArgsConstructor
public class SessionTokenValidator {

	private final JwtDecoder jwtDecoder;
	private final RedisAuthSessionStore sessionStore;

	public Optional<AuthenticatedUser> validate(String accessToken) {
		Jwt jwt = jwtDecoder.decode(accessToken);
		String sessionId = jwt.getClaimAsString("sid");
		return sessionStore.findBySessionId(sessionId)
			.filter(session -> session.status() == UserStatus.active)
			.map(session -> new AuthenticatedUser(
				session.userId(),
				session.email(),
				UserRole.valueOf(jwt.getClaimAsString("role")),
				session.status()
			));
	}
}
