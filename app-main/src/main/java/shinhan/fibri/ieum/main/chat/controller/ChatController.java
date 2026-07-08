package shinhan.fibri.ieum.main.chat.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.common.chat.domain.RoomType;
import shinhan.fibri.ieum.main.chat.dto.ChatCursorPage;
import shinhan.fibri.ieum.main.chat.dto.ChatMessageResponse;
import shinhan.fibri.ieum.main.chat.dto.ChatRoomDetailResponse;
import shinhan.fibri.ieum.main.chat.dto.ChatRoomResponse;
import shinhan.fibri.ieum.main.chat.dto.ChatRoomSummaryResponse;
import shinhan.fibri.ieum.main.chat.dto.DirectRoomRequest;
import shinhan.fibri.ieum.main.chat.service.ChatService;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@GetMapping("/rooms")
	public ResponseEntity<List<ChatRoomSummaryResponse>> listRooms(
		@AuthenticationPrincipal AuthenticatedUser principal,
		@RequestParam(required = false) RoomType type
	) {
		return ResponseEntity.ok(chatService.listRooms(principal, type));
	}

	@PostMapping("/rooms/direct")
	public ResponseEntity<ChatRoomResponse> createDirectRoom(
		@AuthenticationPrincipal AuthenticatedUser principal,
		@Valid @RequestBody DirectRoomRequest request
	) {
		return ResponseEntity.ok(chatService.createDirectRoom(principal, request.friendId()));
	}

	@GetMapping("/rooms/{roomId}")
	public ResponseEntity<ChatRoomDetailResponse> getRoom(
		@AuthenticationPrincipal AuthenticatedUser principal,
		@PathVariable Long roomId
	) {
		return ResponseEntity.ok(chatService.getRoom(principal, roomId));
	}

	@GetMapping("/rooms/{roomId}/messages")
	public ResponseEntity<ChatCursorPage<ChatMessageResponse>> listMessages(
		@AuthenticationPrincipal AuthenticatedUser principal,
		@PathVariable Long roomId,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size
	) {
		return ResponseEntity.ok(chatService.listMessages(principal, roomId, cursor, size));
	}
}
