package shinhan.fibri.ieum.main.friend.dto;

import java.time.OffsetDateTime;
import shinhan.fibri.ieum.common.auth.domain.User;

public record BlockedUserResponse(
	Long userId,
	String nickname,
	String profileImageUrl,
	OffsetDateTime blockedAt
) {

	public static BlockedUserResponse from(User user, OffsetDateTime blockedAt) {
		return new BlockedUserResponse(
			user.getId(),
			user.getNickname(),
			profileImageUrl(user),
			blockedAt
		);
	}

	private static String profileImageUrl(User user) {
		if (user.getProfileFileId() == null) {
			return null;
		}
		return "/api/v1/files/" + user.getProfileFileId();
	}
}
