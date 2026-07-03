package shinhan.fibri.ieum.main.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.main.auth.dto.RefreshResponse;
import shinhan.fibri.ieum.main.auth.exception.InvalidRefreshTokenException;
import shinhan.fibri.ieum.main.auth.exception.RefreshTokenReusedException;
import shinhan.fibri.ieum.main.auth.session.AccessTokenIssuer;
import shinhan.fibri.ieum.main.auth.session.AuthSession;
import shinhan.fibri.ieum.main.auth.session.OpaqueTokenGenerator;
import shinhan.fibri.ieum.main.auth.session.RedisAuthSessionStore;
import shinhan.fibri.ieum.main.auth.session.Sha256TokenHasher;

@Service
@RequiredArgsConstructor
public class RefreshService {

	private final RedisAuthSessionStore sessionStore;
	private final Sha256TokenHasher tokenHasher;
	private final OpaqueTokenGenerator tokenGenerator;
	private final AccessTokenIssuer accessTokenIssuer;

	public RefreshResult refresh(String refreshToken) {
		String refreshTokenHash = tokenHasher.hash(refreshToken);
		AuthSession session = sessionStore.findByRefreshTokenHash(refreshTokenHash)
			.orElseThrow(InvalidRefreshTokenException::new);
		if (session.status() != UserStatus.active) {
			throw new InvalidRefreshTokenException();
		}
		if (refreshTokenHash.equals(session.prevRefreshTokenHash())) {
			sessionStore.revokeAllSessionsOfUser(session.userId());
			throw new RefreshTokenReusedException();
		}
		if (!refreshTokenHash.equals(session.refreshTokenHash())) {
			throw new InvalidRefreshTokenException();
		}

		String newRefreshToken = tokenGenerator.generate();
		String csrfToken = tokenGenerator.generate();
		String newRefreshTokenHash = tokenHasher.hash(newRefreshToken);
		String accessToken = accessTokenIssuer.issue(session.userId(), session.sessionId(), session.email(), session.role());
		sessionStore.rotateRefreshToken(session, newRefreshTokenHash);

		return new RefreshResult(
			new RefreshResponse(session.userId(), session.role()),
			accessToken,
			newRefreshToken,
			csrfToken
		);
	}
}
