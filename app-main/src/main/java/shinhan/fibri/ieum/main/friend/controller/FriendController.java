package shinhan.fibri.ieum.main.friend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.main.friend.service.FriendService;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

	private final FriendService friendService;

	@PostMapping("/{userId}")
	public ResponseEntity<Void> requestFriend(
		@AuthenticationPrincipal AuthenticatedUser principal,
		@PathVariable Long userId
	) {
		friendService.requestFriend(principal, userId);
		return ResponseEntity.noContent().build();
	}
}
