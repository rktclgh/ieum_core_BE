package shinhan.fibri.ieum.main.friend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.main.auth.session.SessionTokenValidator;
import shinhan.fibri.ieum.main.friend.exception.AlreadyFriendsException;
import shinhan.fibri.ieum.main.friend.exception.BlockedFriendshipException;
import shinhan.fibri.ieum.main.friend.exception.FriendRequestExistsException;
import shinhan.fibri.ieum.main.friend.exception.SelfFriendRequestException;
import shinhan.fibri.ieum.main.friend.service.FriendService;
import shinhan.fibri.ieum.main.user.exception.UserNotFoundException;

@WebMvcTest(FriendController.class)
@AutoConfigureMockMvc(addFilters = false)
class FriendControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FriendService friendService;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
		reset(friendService);
	}

	@Test
	void requestFriendReturnsNoContent() throws Exception {
		mockMvc.perform(post("/api/v1/friends/{userId}", 7L).with(authenticated()))
			.andExpect(status().isNoContent());

		verify(friendService).requestFriend(any(AuthenticatedUser.class), eq(7L));
	}

	@Test
	void mapsSelfFriendRequestToBadRequest() throws Exception {
		doThrow(new SelfFriendRequestException())
			.when(friendService).requestFriend(any(AuthenticatedUser.class), eq(42L));

		mockMvc.perform(post("/api/v1/friends/{userId}", 42L).with(authenticated()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("SELF_FRIEND_REQUEST")));
	}

	@Test
	void mapsBlockedFriendshipToForbidden() throws Exception {
		doThrow(new BlockedFriendshipException())
			.when(friendService).requestFriend(any(AuthenticatedUser.class), eq(7L));

		mockMvc.perform(post("/api/v1/friends/{userId}", 7L).with(authenticated()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("BLOCKED")));
	}

	@Test
	void mapsUserNotFoundToNotFound() throws Exception {
		doThrow(new UserNotFoundException())
			.when(friendService).requestFriend(any(AuthenticatedUser.class), eq(7L));

		mockMvc.perform(post("/api/v1/friends/{userId}", 7L).with(authenticated()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("USER_NOT_FOUND")));
	}

	@Test
	void mapsFriendRequestExistsToConflict() throws Exception {
		doThrow(new FriendRequestExistsException())
			.when(friendService).requestFriend(any(AuthenticatedUser.class), eq(7L));

		mockMvc.perform(post("/api/v1/friends/{userId}", 7L).with(authenticated()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("REQUEST_EXISTS")));
	}

	@Test
	void mapsAlreadyFriendsToConflict() throws Exception {
		doThrow(new AlreadyFriendsException())
			.when(friendService).requestFriend(any(AuthenticatedUser.class), eq(7L));

		mockMvc.perform(post("/api/v1/friends/{userId}", 7L).with(authenticated()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("ALREADY_FRIENDS")));
	}

	private static RequestPostProcessor authenticated() {
		return request -> {
			AuthenticatedUser principal = new AuthenticatedUser(
				42L,
				"user@example.com",
				UserRole.user,
				UserStatus.active
			);
			SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(principal, null));
			return request;
		};
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		FriendService friendService() {
			return mock(FriendService.class);
		}

		@Bean
		@Primary
		SessionTokenValidator sessionTokenValidator() {
			return mock(SessionTokenValidator.class);
		}

		@Bean
		WebMvcConfigurer authenticationPrincipalArgumentResolverConfigurer() {
			return new WebMvcConfigurer() {
				@Override
				public void addArgumentResolvers(java.util.List<HandlerMethodArgumentResolver> resolvers) {
					resolvers.add(new AuthenticationPrincipalArgumentResolver());
				}
			};
		}
	}
}
