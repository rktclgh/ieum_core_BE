package shinhan.fibri.ieum.main.admin.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.main.admin.dto.AdminLoginResponse;
import shinhan.fibri.ieum.main.auth.dto.LoginRequest;
import shinhan.fibri.ieum.main.auth.dto.LoginResponse;
import shinhan.fibri.ieum.main.auth.exception.EmailNotVerifiedException;
import shinhan.fibri.ieum.main.auth.exception.InvalidCredentialsException;
import shinhan.fibri.ieum.main.auth.exception.SuspendedUserException;
import shinhan.fibri.ieum.main.auth.service.LoginResult;
import shinhan.fibri.ieum.main.auth.service.LoginService;

@Service
@RequiredArgsConstructor
public class AdminLoginService {

	private static final Logger log = LoggerFactory.getLogger(AdminLoginService.class);
	private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

	private final LoginService loginService;

	// Keep the role gate in the same transaction as LoginService.login.
	// On rejection, rollback cancels LoginLog and prevents after-commit Redis session storage.
	@Transactional
	public AdminLoginResult login(LoginRequest request) {
		LoginResult result;
		try {
			result = loginService.login(request);
		} catch (EmailNotVerifiedException | SuspendedUserException exception) {
			log.warn("Admin login rejected (account state): reason={}", exception.getClass().getSimpleName());
			throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}

		LoginResponse response = result.response();
		if (response.role() != UserRole.admin) {
			log.warn("Admin login rejected (not admin): userId={}", response.userId());
			throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}

		return new AdminLoginResult(
			new AdminLoginResponse(response.userId(), response.role(), response.passwordResetRequired()),
			result.accessToken(),
			result.refreshToken(),
			result.csrfToken()
		);
	}
}
