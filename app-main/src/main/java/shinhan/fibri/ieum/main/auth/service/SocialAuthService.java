package shinhan.fibri.ieum.main.auth.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.common.auth.domain.User;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.auth.repository.UserRepository;
import shinhan.fibri.ieum.main.auth.domain.LoginLog;
import shinhan.fibri.ieum.main.auth.dto.SocialAuthRequest;
import shinhan.fibri.ieum.main.auth.dto.SocialAuthResponse;
import shinhan.fibri.ieum.main.auth.exception.SuspendedUserException;
import shinhan.fibri.ieum.main.auth.repository.LoginLogRepository;
import shinhan.fibri.ieum.main.auth.session.IssuedAuthSession;
import shinhan.fibri.ieum.main.auth.session.OpaqueTokenGenerator;
import shinhan.fibri.ieum.main.auth.session.SessionIssuer;

@Service
@RequiredArgsConstructor
public class SocialAuthService {

	private static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(30);
	private static final int SIGNUP_TOKEN_TTL_SECONDS = 1800;

	private final SocialIdentityVerifier identityVerifier;
	private final UserRepository userRepository;
	private final LoginLogRepository loginLogRepository;
	private final SessionIssuer sessionIssuer;
	private final SocialSignupTokenStore signupTokenStore;
	private final OpaqueTokenGenerator tokenGenerator;

	@Transactional
	public SocialAuthResult start(SocialAuthRequest request) {
		VerifiedSocialIdentity identity = identityVerifier.verify(request);
		return userRepository
			.findByProviderAndProviderUidAndDeletedAtIsNull(identity.provider(), identity.providerUid())
			.map(user -> loginExistingUser(user, identity))
			.orElseGet(() -> issueSignupToken(identity));
	}

	private SocialAuthResult loginExistingUser(User user, VerifiedSocialIdentity identity) {
		if (user.getStatus() == UserStatus.suspended) {
			throw new SuspendedUserException();
		}
		loginLogRepository.save(LoginLog.socialLogin(user, identity.provider()));
		IssuedAuthSession issuedSession = sessionIssuer.issue(user);
		return new SocialAuthResult(
			SocialAuthResponse.existingUser(user.getId(), user.getRole()),
			issuedSession.accessToken(),
			issuedSession.refreshToken(),
			issuedSession.csrfToken()
		);
	}

	private SocialAuthResult issueSignupToken(VerifiedSocialIdentity identity) {
		String signupToken = tokenGenerator.generate();
		signupTokenStore.save(
			signupToken,
			new SocialSignupIdentity(
				identity.provider(),
				identity.providerUid(),
				identity.email(),
				identity.emailVerified()
			),
			SIGNUP_TOKEN_TTL
		);
		return new SocialAuthResult(
			SocialAuthResponse.newUser(signupToken, SIGNUP_TOKEN_TTL_SECONDS),
			null,
			null,
			null
		);
	}
}
