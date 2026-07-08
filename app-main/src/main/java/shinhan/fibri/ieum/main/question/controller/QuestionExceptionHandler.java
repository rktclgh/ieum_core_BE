package shinhan.fibri.ieum.main.question.controller;

import java.util.Comparator;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import shinhan.fibri.ieum.main.auth.dto.AuthErrorResponse;
import shinhan.fibri.ieum.main.question.exception.InvalidQuestionRequestException;
import shinhan.fibri.ieum.main.question.exception.QuestionForbiddenException;
import shinhan.fibri.ieum.main.question.exception.QuestionNotFoundException;

@RestControllerAdvice(assignableTypes = QuestionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QuestionExceptionHandler {

	@ExceptionHandler(QuestionNotFoundException.class)
	public ResponseEntity<AuthErrorResponse> handleQuestionNotFound(QuestionNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new AuthErrorResponse("QUESTION_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(QuestionForbiddenException.class)
	public ResponseEntity<AuthErrorResponse> handleQuestionForbidden(QuestionForbiddenException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(new AuthErrorResponse("FORBIDDEN", exception.getMessage()));
	}

	@ExceptionHandler(InvalidQuestionRequestException.class)
	public ResponseEntity<AuthErrorResponse> handleInvalidQuestionRequest(InvalidQuestionRequestException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse(
				exception.code(),
				exception.getMessage(),
				List.of(new AuthErrorResponse.FieldError(exception.field(), exception.getMessage()))
			));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<AuthErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse("INVALID_CURSOR", exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<AuthErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception) {
		List<AuthErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(fieldError -> new AuthErrorResponse.FieldError(
				fieldError.getField(),
				fieldError.getDefaultMessage()
			))
			.toList();
		List<AuthErrorResponse.FieldError> globalErrors = exception.getBindingResult()
			.getGlobalErrors()
			.stream()
			.map(this::toGlobalError)
			.toList();

		List<AuthErrorResponse.FieldError> validationErrors = java.util.stream.Stream
			.concat(fieldErrors.stream(), globalErrors.stream())
			.sorted(Comparator.comparing(AuthErrorResponse.FieldError::field))
			.toList();

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse("VALIDATION_FAILED", "Request validation failed", validationErrors));
	}

	private AuthErrorResponse.FieldError toGlobalError(ObjectError error) {
		return new AuthErrorResponse.FieldError(error.getObjectName(), error.getDefaultMessage());
	}
}
