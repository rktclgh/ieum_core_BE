package shinhan.fibri.ieum.main.place.controller;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import shinhan.fibri.ieum.main.auth.dto.AuthErrorResponse;
import shinhan.fibri.ieum.main.place.exception.PlaceRequestException;

@RestControllerAdvice(assignableTypes = PlaceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlaceExceptionHandler {

	@ExceptionHandler(PlaceRequestException.class)
	public ResponseEntity<AuthErrorResponse> handleRequest(PlaceRequestException exception) {
		return ResponseEntity.status(exception.status())
			.body(new AuthErrorResponse(
				exception.code(),
				exception.getMessage(),
				List.of(new AuthErrorResponse.FieldError(exception.field(), exception.getMessage()))
			));
	}
}
