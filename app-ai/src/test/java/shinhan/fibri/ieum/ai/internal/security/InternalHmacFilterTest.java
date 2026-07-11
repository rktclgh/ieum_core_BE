package shinhan.fibri.ieum.ai.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalHmacFilterTest {

	private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
	private static final Instant NOW = Instant.ofEpochSecond(1_725_000_000L);

	private InternalHmacFilter filter;

	@BeforeEach
	void setUp() {
		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(Base64.getEncoder().encodeToString(SECRET));
		properties.setMaxBodyBytes(64);
		properties.setReplayMaxEntries(64);
		properties.setReplayTtlSeconds(120);
		properties.afterPropertiesSet();
		filter = new InternalHmacFilter(new InternalHmacVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC)),
				properties);
	}

	@Test
	void protectsAiV1RequestsAndPassesCachedBodyToChain() throws ServletException, IOException {
		byte[] body = "{\"question\":\"hi\"}".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = new MockHttpServletRequest("post", "/ai/v1/questions");
		request.setQueryString("z=9");
		request.setContent(body);
		sign(request, "POST", "/ai/v1/questions", "z=9", body);
		MockHttpServletResponse response = new MockHttpServletResponse();
		CapturingFilterChain chain = new CapturingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
		assertThat(chain.invoked).isTrue();
		assertThat(chain.body).isEqualTo(body);
	}

	@Test
	void rejectsDuplicateContractHeaders() throws ServletException, IOException {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/v1/questions");
		request.setContent(body);
		sign(request, "POST", "/ai/v1/questions", "", body);
		request.addHeader("X-Internal-Service", "app-main");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
	}

	@Test
	void rejectsEncodedBodiesBeforeSignatureVerification() throws ServletException, IOException {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/v1/questions");
		request.setContent(body);
		request.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
		sign(request, "POST", "/ai/v1/questions", "", body);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void rejectsMultipleContentEncodingValues() throws ServletException, IOException {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/v1/questions");
		request.setContent(body);
		request.addHeader(HttpHeaders.CONTENT_ENCODING, "identity");
		request.addHeader(HttpHeaders.CONTENT_ENCODING, "identity");
		sign(request, "POST", "/ai/v1/questions", "", body);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void rejectsBodyLargerThanConfiguredLimitBeforeReadingAllBytes() throws ServletException, IOException {
		byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/v1/questions");
		request.setContent(body);
		request.addHeader(HttpHeaders.CONTENT_LENGTH, body.length);
		MockHttpServletResponse response = new MockHttpServletResponse();

		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(Base64.getEncoder().encodeToString(SECRET));
		properties.setMaxBodyBytes(9);
		properties.afterPropertiesSet();
		InternalHmacFilter limitedFilter = new InternalHmacFilter(
				new InternalHmacVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC)), properties);

		limitedFilter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
	}

	@Test
	void rejectsBodyLargerThanConfiguredLimitWhenContentLengthIsUnknown() throws ServletException, IOException {
		byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/v1/questions") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setContent(body);
		sign(request, "POST", "/ai/v1/questions", "", body);
		MockHttpServletResponse response = new MockHttpServletResponse();

		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(Base64.getEncoder().encodeToString(SECRET));
		properties.setMaxBodyBytes(9);
		properties.afterPropertiesSet();
		InternalHmacFilter limitedFilter = new InternalHmacFilter(
				new InternalHmacVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC)), properties);

		limitedFilter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
	}

	@Test
	void skipsActuatorHealth() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
	}

	private void sign(MockHttpServletRequest request, String method, String rawPath, String rawQuery, byte[] body) {
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		values.put("X-Internal-Service", "app-main");
		values.put("X-Internal-Key-Id", "current-key");
		values.put("X-Internal-Timestamp", Long.toString(NOW.getEpochSecond()));
		values.put("X-Internal-Request-Id", UUID.randomUUID().toString());
		values.put("X-Internal-Body-SHA256", Hex.sha256(body));
		values.put("X-Internal-Signature", signature(values, method, rawPath, rawQuery));
		values.forEach(request::addHeader);
	}

	private String signature(Map<String, String> values, String method, String rawPath, String rawQuery) {
		String canonical = String.join("\n",
				"v1",
				values.get("X-Internal-Service"),
				values.get("X-Internal-Key-Id"),
				values.get("X-Internal-Timestamp"),
				values.get("X-Internal-Request-Id"),
				method.toUpperCase(),
				rawPath,
				rawQuery,
				values.get("X-Internal-Body-SHA256"));
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
			return Hex.lower(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static final class CapturingFilterChain extends MockFilterChain {

		private boolean invoked;
		private byte[] body;

		@Override
		public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
				throws IOException, ServletException {
			invoked = true;
			body = ((HttpServletRequest) request).getInputStream().readAllBytes();
			super.doFilter(request, response);
		}

	}

}
