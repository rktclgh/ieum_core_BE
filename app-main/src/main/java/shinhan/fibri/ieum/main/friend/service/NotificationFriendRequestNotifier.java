package shinhan.fibri.ieum.main.friend.service;

import org.springframework.stereotype.Component;
import shinhan.fibri.ieum.common.auth.repository.UserRepository;
import shinhan.fibri.ieum.main.notification.domain.NotificationType;
import shinhan.fibri.ieum.main.notification.service.NotificationPublisher;

@Component
public class NotificationFriendRequestNotifier implements FriendRequestNotifier {

	private final UserRepository userRepository;
	private final NotificationPublisher notificationPublisher;

	public NotificationFriendRequestNotifier(UserRepository userRepository, NotificationPublisher notificationPublisher) {
		this.userRepository = userRepository;
		this.notificationPublisher = notificationPublisher;
	}

	@Override
	public void notifyRequested(Long requesterId, Long addresseeId) {
		notificationPublisher.publishDurable(
			addresseeId,
			NotificationType.friend,
			"친구 요청",
			requesterNickname(requesterId) + "님이 친구 요청을 보냈어요",
			requesterId
		);
	}

	private String requesterNickname(Long requesterId) {
		try {
			return userRepository.findByIdAndDeletedAtIsNull(requesterId)
				.map(user -> user.getNickname())
				.orElse("사용자");
		} catch (RuntimeException exception) {
			return "사용자";
		}
	}
}
