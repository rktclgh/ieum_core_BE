package shinhan.fibri.ieum.main.file.dto;

public record FileStreamResponse(
	String contentType,
	Long contentLength,
	byte[] bytes
) {
}
