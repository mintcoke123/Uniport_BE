package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.FriendInviteAcceptResponseDTO;
import com.uniport.dto.FriendInviteCreateResponseDTO;
import com.uniport.dto.FriendInviteDetailResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.FriendInviteService;
import com.uniport.service.PointSocialDataService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendsControllerTest {

    @Test
    void deleteFriend_returnsNoContentAndDelegatesToService() throws Exception {
        PointSocialDataService pointSocialDataService = mock(PointSocialDataService.class);
        FriendInviteService friendInviteService = mock(FriendInviteService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder()
                .studentId("20262001")
                .password("password")
                .nickname("controller-friend-removal")
                .build();
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendsController(pointSocialDataService, friendInviteService, currentUserResolver)).build();

        mockMvc.perform(delete("/api/friends/USER_99").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());

        verify(pointSocialDataService).deleteFriend(currentUser, "USER_99");
    }

    @Test
    void createInvite_returnsCreatedAndDelegatesToService() throws Exception {
        PointSocialDataService pointSocialDataService = mock(PointSocialDataService.class);
        FriendInviteService friendInviteService = mock(FriendInviteService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder()
                .studentId("20262002")
                .password("password")
                .nickname("controller-invite-create")
                .build();
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        when(friendInviteService.createInvite(currentUser))
                .thenReturn(FriendInviteCreateResponseDTO.builder()
                        .inviteCode("abc123")
                        .inviteUrl("https://uniportbe-production.up.railway.app/friend-invite?inviteCode=abc123")
                        .expiresAt("2026-05-16T12:00:00Z")
                        .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendsController(pointSocialDataService, friendInviteService, currentUserResolver)).build();

        mockMvc.perform(post("/api/friends/invites").header("Authorization", "Bearer test-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inviteCode").value("abc123"))
                .andExpect(jsonPath("$.inviteUrl").value("https://uniportbe-production.up.railway.app/friend-invite?inviteCode=abc123"));

        verify(friendInviteService).createInvite(currentUser);
    }

    @Test
    void getInviteDetail_returnsInvitePreviewWithoutAuthentication() throws Exception {
        PointSocialDataService pointSocialDataService = mock(PointSocialDataService.class);
        FriendInviteService friendInviteService = mock(FriendInviteService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        when(friendInviteService.getInviteDetail("abc123"))
                .thenReturn(FriendInviteDetailResponseDTO.builder()
                        .inviteCode("abc123")
                        .inviterUserId("USER_1")
                        .inviterNickname("곽건")
                        .inviterProfileImageUrl("https://example.com/profile.png")
                        .status("ACTIVE")
                        .expiresAt("2026-05-16T12:00:00Z")
                        .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendsController(pointSocialDataService, friendInviteService, currentUserResolver)).build();

        mockMvc.perform(get("/api/friends/invites/abc123").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviterUserId").value("USER_1"))
                .andExpect(jsonPath("$.inviterNickname").value("곽건"));

        verify(friendInviteService).getInviteDetail("abc123");
    }

    @Test
    void acceptInvite_returnsAcceptedRelationAndDelegatesToService() throws Exception {
        PointSocialDataService pointSocialDataService = mock(PointSocialDataService.class);
        FriendInviteService friendInviteService = mock(FriendInviteService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder()
                .studentId("20262003")
                .password("password")
                .nickname("controller-invite-accept")
                .build();
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        when(friendInviteService.acceptInvite(currentUser, "abc123"))
                .thenReturn(FriendInviteAcceptResponseDTO.builder()
                        .friendUserId("USER_1")
                        .status("ACCEPTED")
                        .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendsController(pointSocialDataService, friendInviteService, currentUserResolver)).build();

        mockMvc.perform(post("/api/friends/invites/abc123/accept").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendUserId").value("USER_1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(friendInviteService).acceptInvite(currentUser, "abc123");
    }
}
