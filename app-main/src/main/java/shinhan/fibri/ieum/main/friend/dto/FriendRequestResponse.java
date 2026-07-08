package shinhan.fibri.ieum.main.friend.dto;

import java.time.OffsetDateTime;
import shinhan.fibri.ieum.common.auth.domain.User;

public record FriendRequestResponse(
	Long userId,
	String nickname,
	String profileImageUrl,
	OffsetDateTime requestedAt
) {

	public static FriendRequestResponse from(User user, OffsetDateTime requestedAt) {
		return new FriendRequestResponse(
			user.getId(),
			user.getNickname(),
			profileImageUrl(user),
			requestedAt
		);
	}

	private static String profileImageUrl(User user) {
		if (user.getProfileFileId() == null) {
			return null;
		}
		return "/api/v1/files/" + user.getProfileFileId();
	}
}
