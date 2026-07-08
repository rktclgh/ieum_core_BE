package shinhan.fibri.ieum.main.answer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateAnswerRequest(
	@NotBlank
	@Size(max = 5000)
	String content,

	@Size(max = 10)
	List<UUID> imageFileIds
) {
}
