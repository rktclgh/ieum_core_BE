package shinhan.fibri.ieum.main.friend.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import shinhan.fibri.ieum.common.auth.domain.User;
import shinhan.fibri.ieum.common.auth.repository.UserRepository;
import shinhan.fibri.ieum.main.notification.domain.NotificationType;
import shinhan.fibri.ieum.main.notification.service.NotificationPublisher;

class NotificationFriendRequestNotifierTest {

	@Test
	void publishesDurableFriendRequestWithRequesterNickname() {
		UserRepository userRepository = mock(UserRepository.class);
		NotificationPublisher notificationPublisher = mock(NotificationPublisher.class);
		NotificationFriendRequestNotifier notifier = new NotificationFriendRequestNotifier(userRepository, notificationPublisher);
		User requester = mock(User.class);
		when(requester.getNickname()).thenReturn("요청자");
		when(userRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(requester));

		notifier.notifyRequested(42L, 77L);

		verify(notificationPublisher).publishDurable(
			77L,
			NotificationType.friend,
			"친구 요청",
			"요청자님이 친구 요청을 보냈어요",
			42L
		);
	}
}
