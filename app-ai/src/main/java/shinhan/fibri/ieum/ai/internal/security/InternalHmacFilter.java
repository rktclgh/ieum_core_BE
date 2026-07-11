package shinhan.fibri.ieum.ai.internal.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalHmacFilter extends OncePerRequestFilter {

	private final InternalHmacVerifier verifier;
	private final InternalHmacProperties properties;

	public InternalHmacFilter(InternalHmacVerifier verifier, InternalHmacProperties properties) {
		this.verifier = verifier;
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/ai/v1/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!hasValidContentEncoding(request)) {
			response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
			return;
		}
		if (request.getContentLengthLong() > properties.getMaxBodyBytes()) {
			response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}

		byte[] body = request.getInputStream().readNBytes(properties.getMaxBodyBytes() + 1);
		if (body.length > properties.getMaxBodyBytes()) {
			response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}
		InternalHmacVerificationResult result = verifier.verify(new InternalHmacVerificationRequest(
				request.getMethod(),
				request.getRequestURI(),
				request.getQueryString() == null ? "" : request.getQueryString(),
				body,
				headersFrom(request)));
		if (!result.accepted()) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
	}

	private InternalHmacHeaders headersFrom(HttpServletRequest request) {
		InternalHmacHeaders headers = new InternalHmacHeaders();
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			Enumeration<String> values = request.getHeaders(name);
			for (String value : Collections.list(values)) {
				headers.add(name.toLowerCase(Locale.ROOT), value);
			}
		}
		return headers;
	}

	private boolean hasValidContentEncoding(HttpServletRequest request) {
		Enumeration<String> values = request.getHeaders(HttpHeaders.CONTENT_ENCODING);
		if (!values.hasMoreElements()) {
			return true;
		}
		String first = values.nextElement();
		return !values.hasMoreElements() && "identity".equalsIgnoreCase(first);
	}

}
