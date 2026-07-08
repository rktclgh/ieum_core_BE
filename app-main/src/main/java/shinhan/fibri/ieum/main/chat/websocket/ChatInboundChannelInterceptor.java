package shinhan.fibri.ieum.main.chat.websocket;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.chat.repository.ChatMemberRepository;
import shinhan.fibri.ieum.main.auth.session.RedisAuthSessionStore;
import shinhan.fibri.ieum.main.chat.service.ChatMessageRateLimiter;

@Component
public class ChatInboundChannelInterceptor implements ChannelInterceptor {

	private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/rooms/(\\d+)$");
	private static final Pattern SEND_DESTINATION_PATTERN = Pattern.compile("^/app/rooms/(\\d+)/send$");

	private final ChatMemberRepository chatMemberRepository;
	private final RedisAuthSessionStore sessionStore;
	private final ChatMessageRateLimiter rateLimiter;
	private final ChatWebSocketErrorSender errorSender;

	public ChatInboundChannelInterceptor(
		ChatMemberRepository chatMemberRepository,
		RedisAuthSessionStore sessionStore,
		ChatMessageRateLimiter rateLimiter,
		@Lazy ChatWebSocketErrorSender errorSender
	) {
		this.chatMemberRepository = chatMemberRepository;
		this.sessionStore = sessionStore;
		this.rateLimiter = rateLimiter;
		this.errorSender = errorSender;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
		StompCommand command = accessor.getCommand();
		if (command == StompCommand.CONNECT) {
			return handleConnect(message, accessor);
		}
		if (command == StompCommand.SUBSCRIBE) {
			return handleSubscribe(message, accessor);
		}
		if (command == StompCommand.SEND) {
			return handleSend(message, accessor);
		}
		return message;
	}

	private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
		ChatWebSocketPrincipal principal = handshakePrincipal(accessor).orElse(null);
		if (principal == null) {
			return null;
		}
		accessor.setUser(principal);
		return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
	}

	private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
		ChatWebSocketPrincipal principal = currentPrincipal(accessor).orElse(null);
		Long roomId = parseRoomId(accessor.getDestination(), ROOM_TOPIC_PATTERN).orElse(null);
		if (principal == null || roomId == null) {
			return message;
		}
		if (!isActiveRoomMember(roomId, principal)) {
			sendError(principal, "NOT_ROOM_MEMBER", "Room membership is required", roomId);
			return null;
		}
		return message;
	}

	private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor) {
		ChatWebSocketPrincipal principal = currentPrincipal(accessor).orElse(null);
		Long roomId = parseRoomId(accessor.getDestination(), SEND_DESTINATION_PATTERN).orElse(null);
		if (principal == null || roomId == null) {
			return message;
		}
		if (!hasActiveSession(principal)) {
			sendError(principal, "INVALID_SESSION", "Chat session is invalid", roomId);
			return null;
		}
		if (!rateLimiter.tryConsumeSend(principal.authenticatedUser().userId())) {
			sendError(principal, "RATE_LIMITED", "Too many chat messages", roomId);
			return null;
		}
		if (!isActiveRoomMember(roomId, principal)) {
			sendError(principal, "NOT_ROOM_MEMBER", "Room membership is required", roomId);
			return null;
		}
		return message;
	}

	private Optional<ChatWebSocketPrincipal> handshakePrincipal(StompHeaderAccessor accessor) {
		if (accessor.getSessionAttributes() == null) {
			return Optional.empty();
		}
		Object principal = accessor.getSessionAttributes().get(ChatWebSocketPrincipal.ATTRIBUTE_NAME);
		if (principal instanceof ChatWebSocketPrincipal chatPrincipal) {
			return Optional.of(chatPrincipal);
		}
		return Optional.empty();
	}

	private Optional<ChatWebSocketPrincipal> currentPrincipal(StompHeaderAccessor accessor) {
		if (accessor.getUser() instanceof ChatWebSocketPrincipal principal) {
			return Optional.of(principal);
		}
		return Optional.empty();
	}

	private Optional<Long> parseRoomId(String destination, Pattern pattern) {
		if (destination == null) {
			return Optional.empty();
		}
		Matcher matcher = pattern.matcher(destination);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(Long.valueOf(matcher.group(1)));
	}

	private boolean hasActiveSession(ChatWebSocketPrincipal principal) {
		return sessionStore.findBySessionId(principal.sessionId())
			.filter(session -> session.status() == UserStatus.active)
			.filter(session -> session.userId().equals(principal.authenticatedUser().userId()))
			.isPresent();
	}

	private boolean isActiveRoomMember(Long roomId, ChatWebSocketPrincipal principal) {
		return chatMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(
			roomId,
			principal.authenticatedUser().userId()
		);
	}

	private void sendError(ChatWebSocketPrincipal principal, String code, String message, Long roomId) {
		errorSender.send(principal, new ChatWebSocketErrorResponse(code, message, roomId));
	}
}
