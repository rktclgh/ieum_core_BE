package shinhan.fibri.ieum.main.file.dto;

import java.io.InputStream;

public record FileStreamResponse(
	String contentType,
	Long contentLength,
	InputStream body
) {
}
