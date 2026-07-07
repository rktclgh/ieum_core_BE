package shinhan.fibri.ieum.main.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.common.auth.domain.AuthProvider;
import shinhan.fibri.ieum.common.auth.domain.User;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.auth.repository.UserRepository;
import shinhan.fibri.ieum.common.auth.validation.AuthEmailNormalizer;
import shinhan.fibri.ieum.main.auth.domain.LoginLog;
import shinhan.fibri.ieum.main.auth.dto.LoginRequest;
import shinhan.fibri.ieum.main.auth.dto.LoginResponse;
import shinhan.fibri.ieum.main.auth.exception.EmailNotVerifiedException;
import shinhan.fibri.ieum.main.auth.exception.InvalidCredentialsException;
import shinhan.fibri.ieum.main.auth.exception.SuspendedUserException;
import shinhan.fibri.ieum.main.auth.repository.LoginLogRepository;
import shinhan.fibri.ieum.main.auth.session.IssuedAuthSession;
import shinhan.fibri.ieum.main.auth.session.SessionIssuer;

@Service
@RequiredArgsConstructor
public class LoginService {

	private static final String DUMMY_PASSWORD_HASH =
		"$2a$10$N9qo8uLOickgx2ZMRZoMye.IjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final LoginLogRepository loginLogRepository;
	private final SessionIssuer sessionIssuer;

	@Transactional
	public LoginResult login(LoginRequest request) {
		String email = AuthEmailNormalizer.normalize(request.email());
		User user = userRepository.findByEmailAndProviderAndDeletedAtIsNull(email, AuthProvider.email)
			.orElse(null);
		if (user == null) {
			passwordHasher.matches(request.password(), DUMMY_PASSWORD_HASH);
			throw new InvalidCredentialsException();
		}
		if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		if (!user.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		if (user.getStatus() == UserStatus.suspended) {
			throw new SuspendedUserException();
		}

		loginLogRepository.save(LoginLog.emailLogin(user));

		IssuedAuthSession issuedSession = sessionIssuer.issue(user);

		return new LoginResult(
			new LoginResponse(user.getId(), user.getRole(), user.isPasswordResetRequired()),
			issuedSession.accessToken(),
			issuedSession.refreshToken(),
			issuedSession.csrfToken()
		);
	}
}
