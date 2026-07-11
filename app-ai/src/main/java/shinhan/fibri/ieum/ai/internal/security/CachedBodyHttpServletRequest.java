package shinhan.fibri.ieum.ai.internal.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

	private final byte[] body;

	CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
		super(request);
		this.body = body.clone();
	}

	@Override
	public ServletInputStream getInputStream() {
		ByteArrayInputStream input = new ByteArrayInputStream(body);
		return new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return input.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
				throw new UnsupportedOperationException("Async request body reads are not supported");
			}

			@Override
			public int read() {
				return input.read();
			}
		};
	}

	@Override
	public BufferedReader getReader() throws IOException {
		String encoding = getCharacterEncoding();
		if (encoding == null) {
			encoding = StandardCharsets.UTF_8.name();
		}
		return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
	}

}
