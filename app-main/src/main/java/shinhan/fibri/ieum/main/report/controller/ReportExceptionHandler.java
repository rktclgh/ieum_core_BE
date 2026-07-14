package shinhan.fibri.ieum.main.report.controller;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import shinhan.fibri.ieum.main.answer.exception.AnswerNotFoundException;
import shinhan.fibri.ieum.main.auth.dto.AuthErrorResponse;
import shinhan.fibri.ieum.main.chat.exception.NotRoomMemberException;
import shinhan.fibri.ieum.main.report.exception.ReportMessageNotFoundException;

@RestControllerAdvice(assignableTypes = {ReportController.class, AnswerReportController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReportExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<AuthErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		List<AuthErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new AuthErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse("VALIDATION_FAILED", "Request validation failed", fieldErrors));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<AuthErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new AuthErrorResponse(
				"VALIDATION_FAILED",
				"Request validation failed",
				List.of(new AuthErrorResponse.FieldError(exception.getName(), "Invalid value"))
			));
	}

	@ExceptionHandler(AnswerNotFoundException.class)
	public ResponseEntity<AuthErrorResponse> handleAnswerNotFound(AnswerNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new AuthErrorResponse("ANSWER_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(ReportMessageNotFoundException.class)
	public ResponseEntity<AuthErrorResponse> handleMessageNotFound(ReportMessageNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new AuthErrorResponse("REPORT_MESSAGE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(NotRoomMemberException.class)
	public ResponseEntity<AuthErrorResponse> handleNotRoomMember(NotRoomMemberException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(new AuthErrorResponse("NOT_ROOM_MEMBER", exception.getMessage()));
	}
}
