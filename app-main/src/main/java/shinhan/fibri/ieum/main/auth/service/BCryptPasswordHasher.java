package shinhan.fibri.ieum.main.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	@Override
	public String hash(String rawPassword) {
		return encoder.encode(rawPassword);
	}
}
