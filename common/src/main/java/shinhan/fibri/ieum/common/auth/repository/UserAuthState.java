package shinhan.fibri.ieum.common.auth.repository;

import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;

public record UserAuthState(
	String email,
	UserRole role,
	UserStatus status,
	long authVersion
) {
}
