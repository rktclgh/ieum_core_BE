package shinhan.fibri.ieum.main.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import shinhan.fibri.ieum.main.auth.dto.AuthErrorResponse;
import shinhan.fibri.ieum.main.auth.exception.InvalidEmailVerificationCodeException;
import shinhan.fibri.ieum.main.auth.exception.InvalidEmailVerificationTokenException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

	@ExceptionHandler(InvalidEmailVerificationCodeException.class)
	public ResponseEntity<AuthErrorResponse> handleInvalidEmailVerificationCode(
		InvalidEmailVerificationCodeException exception
	) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse("INVALID_EMAIL_VERIFICATION_CODE", exception.getMessage()));
	}

	@ExceptionHandler(InvalidEmailVerificationTokenException.class)
	public ResponseEntity<AuthErrorResponse> handleInvalidEmailVerificationToken(
		InvalidEmailVerificationTokenException exception
	) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse("INVALID_EMAIL_VERIFICATION_TOKEN", exception.getMessage()));
	}
}
