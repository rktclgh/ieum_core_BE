package shinhan.fibri.ieum.main.auth.session;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public class AuthCookieWriter {

	private final AuthSessionProperties properties;

	public AuthCookieWriter(AuthSessionProperties properties) {
		this.properties = properties;
	}

	public void writeLoginCookies(
		HttpServletResponse response,
		String accessToken,
		String refreshToken,
		String csrfToken
	) {
		addCookie(response, "access_token", accessToken, "/", properties.accessTokenMaxAgeSeconds(), true);
		addCookie(response, "refresh_token", refreshToken, "/api/v1/auth", properties.refreshTokenMaxAgeSeconds(), true);
		addCookie(response, "csrf_token", csrfToken, "/", -1, false);
	}

	private void addCookie(
		HttpServletResponse response,
		String name,
		String value,
		String path,
		long maxAgeSeconds,
		boolean httpOnly
	) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
			.path(path)
			.secure(properties.secureCookie())
			.httpOnly(httpOnly)
			.sameSite(properties.sameSite());
		if (!properties.domain().isBlank()) {
			builder.domain(properties.domain());
		}
		if (maxAgeSeconds >= 0) {
			builder.maxAge(maxAgeSeconds);
		}
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}
}
