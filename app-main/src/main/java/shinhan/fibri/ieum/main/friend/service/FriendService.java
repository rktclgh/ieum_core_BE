package shinhan.fibri.ieum.main.friend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import shinhan.fibri.ieum.common.auth.domain.User;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.common.auth.repository.UserRepository;
import shinhan.fibri.ieum.common.friend.domain.Friendship;
import shinhan.fibri.ieum.common.friend.domain.FriendshipStatus;
import shinhan.fibri.ieum.common.friend.repository.FriendshipRepository;
import shinhan.fibri.ieum.main.friend.exception.AlreadyFriendsException;
import shinhan.fibri.ieum.main.friend.exception.BlockedFriendshipException;
import shinhan.fibri.ieum.main.friend.exception.CannotAcceptOwnFriendRequestException;
import shinhan.fibri.ieum.main.friend.exception.FriendRequestExistsException;
import shinhan.fibri.ieum.main.friend.exception.FriendshipNotFoundException;
import shinhan.fibri.ieum.main.friend.exception.SelfFriendActionException;
import shinhan.fibri.ieum.main.friend.exception.SelfFriendRequestException;
import shinhan.fibri.ieum.main.user.exception.UserNotFoundException;

@Service
@RequiredArgsConstructor
public class FriendService {

	private final UserRepository userRepository;
	private final FriendshipRepository friendshipRepository;
	private final FriendRequestNotifier friendRequestNotifier;

	@Transactional
	public void requestFriend(AuthenticatedUser principal, Long targetUserId) {
		User requester = findActiveUser(principal.userId());
		if (requester.getId().equals(targetUserId)) {
			throw new SelfFriendRequestException();
		}
		User addressee = findActiveUser(targetUserId);

		friendshipRepository.findByUserPair(requester.getId(), addressee.getId())
			.ifPresent(this::rejectExistingFriendship);

		friendshipRepository.save(Friendship.request(requester, addressee));
		notifyFriendRequestAfterCommit(requester.getId(), addressee.getId());
	}

	@Transactional
	public void acceptFriendRequest(AuthenticatedUser principal, Long targetUserId) {
		User currentUser = findActiveUser(principal.userId());
		if (currentUser.getId().equals(targetUserId)) {
			throw new SelfFriendActionException();
		}
		User targetUser = findActiveUser(targetUserId);

		Friendship friendship = friendshipRepository.findByUserPair(currentUser.getId(), targetUser.getId())
			.orElseThrow(FriendshipNotFoundException::new);

		if (friendship.getStatus() == FriendshipStatus.blocked) {
			throw new BlockedFriendshipException();
		}
		if (friendship.getStatus() != FriendshipStatus.pending) {
			throw new FriendshipNotFoundException();
		}
		if (friendship.getAddressee().getId().equals(currentUser.getId())) {
			friendship.accept();
			return;
		}
		if (friendship.getRequester().getId().equals(currentUser.getId())) {
			throw new CannotAcceptOwnFriendRequestException();
		}
		throw new FriendshipNotFoundException();
	}

	@Transactional
	public void deleteFriendship(AuthenticatedUser principal, Long targetUserId) {
		User currentUser = findActiveUser(principal.userId());
		if (currentUser.getId().equals(targetUserId)) {
			throw new SelfFriendActionException();
		}
		User targetUser = findActiveUser(targetUserId);

		Friendship friendship = friendshipRepository.findByUserPair(currentUser.getId(), targetUser.getId())
			.orElseThrow(FriendshipNotFoundException::new);

		if (friendship.getStatus() == FriendshipStatus.blocked) {
			throw new BlockedFriendshipException();
		}
		if (friendship.getStatus() != FriendshipStatus.pending && friendship.getStatus() != FriendshipStatus.accepted) {
			throw new FriendshipNotFoundException();
		}
		friendshipRepository.delete(friendship);
	}

	private User findActiveUser(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
			.orElseThrow(UserNotFoundException::new);
	}

	private void rejectExistingFriendship(Friendship friendship) {
		FriendshipStatus status = friendship.getStatus();
		if (status == FriendshipStatus.pending) {
			throw new FriendRequestExistsException();
		}
		if (status == FriendshipStatus.accepted) {
			throw new AlreadyFriendsException();
		}
		if (status == FriendshipStatus.blocked) {
			throw new BlockedFriendshipException();
		}
	}

	private void notifyFriendRequestAfterCommit(Long requesterId, Long addresseeId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			friendRequestNotifier.notifyRequested(requesterId, addresseeId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				friendRequestNotifier.notifyRequested(requesterId, addresseeId);
			}
		});
	}
}
