package com.uniport.service;

import com.uniport.dto.PushTokenRegisterRequestDTO;
import com.uniport.dto.PushTokenResponseDTO;
import com.uniport.dto.PushTokenUnregisterRequestDTO;
import com.uniport.entity.PushPermissionStatus;
import com.uniport.entity.PushPlatform;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.entity.UserPushToken;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserPushTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock
    private UserPushTokenRepository userPushTokenRepository;

    @Mock
    private UserMyPagePreferenceRepository userMyPagePreferenceRepository;

    private PushTokenService pushTokenService;

    @BeforeEach
    void setUp() {
        pushTokenService = new PushTokenService(userPushTokenRepository, userMyPagePreferenceRepository);
    }

    @Test
    void registerToken_createsActiveTokenWhenPermissionGranted() {
        User user = User.builder().id(7L).nickname("push-user").build();
        when(userPushTokenRepository.findByToken("fcm-token")).thenReturn(Optional.empty());
        when(userPushTokenRepository.save(any(UserPushToken.class))).thenAnswer(invocation -> {
            UserPushToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });

        PushTokenResponseDTO response = pushTokenService.registerToken(
                user,
                PushTokenRegisterRequestDTO.builder()
                        .token(" fcm-token ")
                        .platform("android")
                        .permissionStatus("granted")
                        .build()
        );

        ArgumentCaptor<UserPushToken> captor = ArgumentCaptor.forClass(UserPushToken.class);
        verify(userPushTokenRepository).save(captor.capture());
        UserPushToken saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals("fcm-token", saved.getToken());
        assertEquals(PushPlatform.ANDROID, saved.getPlatform());
        assertEquals(PushPermissionStatus.GRANTED, saved.getPermissionStatus());
        assertTrue(saved.isActive());
        assertNotNull(saved.getLastSeenAt());
        assertNull(saved.getRevokedAt());
        assertEquals(1L, response.getId());
        assertEquals("android", response.getPlatform());
        assertEquals("granted", response.getPermissionStatus());
        assertTrue(response.isActive());
    }

    @Test
    void registerToken_reassignsExistingTokenToCurrentUserAndDisablesWhenPermissionDenied() {
        User previousUser = User.builder().id(5L).nickname("previous").build();
        User currentUser = User.builder().id(7L).nickname("current").build();
        UserPushToken existing = UserPushToken.builder()
                .id(3L)
                .user(previousUser)
                .token("same-token")
                .platform(PushPlatform.IOS)
                .permissionStatus(PushPermissionStatus.GRANTED)
                .active(true)
                .build();
        when(userPushTokenRepository.findByToken("same-token")).thenReturn(Optional.of(existing));
        when(userPushTokenRepository.save(existing)).thenReturn(existing);

        PushTokenResponseDTO response = pushTokenService.registerToken(
                currentUser,
                PushTokenRegisterRequestDTO.builder()
                        .token("same-token")
                        .platform("android")
                        .permissionStatus("denied")
                        .build()
        );

        verify(userPushTokenRepository).save(existing);
        assertEquals(currentUser, existing.getUser());
        assertEquals(PushPlatform.ANDROID, existing.getPlatform());
        assertEquals(PushPermissionStatus.DENIED, existing.getPermissionStatus());
        assertFalse(existing.isActive());
        assertNull(existing.getRevokedAt());
        assertEquals(3L, response.getId());
        assertFalse(response.isActive());
    }

    @Test
    void unregisterTokenMarksExistingUserTokenInactiveAndRevoked() {
        User user = User.builder().id(7L).nickname("push-user").build();
        UserPushToken existing = UserPushToken.builder()
                .id(3L)
                .user(user)
                .token("same-token")
                .platform(PushPlatform.ANDROID)
                .permissionStatus(PushPermissionStatus.GRANTED)
                .active(true)
                .build();
        when(userPushTokenRepository.findByToken("same-token")).thenReturn(Optional.of(existing));

        pushTokenService.unregisterToken(
                user,
                PushTokenUnregisterRequestDTO.builder()
                        .token(" same-token ")
                        .build()
        );

        assertFalse(existing.isActive());
        assertNotNull(existing.getRevokedAt());
        verify(userPushTokenRepository).save(existing);
    }

    @Test
    void unregisterTokenIgnoresUnknownToken() {
        User user = User.builder().id(7L).nickname("push-user").build();
        when(userPushTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        pushTokenService.unregisterToken(
                user,
                PushTokenUnregisterRequestDTO.builder()
                        .token("missing-token")
                        .build()
        );

        verify(userPushTokenRepository, never()).save(any());
    }

    @Test
    void getDeliverableTokensRequiresActiveTokenAndPushEnabledPreference() {
        UserPushToken active = UserPushToken.builder()
                .id(1L)
                .token("active-token")
                .active(true)
                .build();
        when(userMyPagePreferenceRepository.findById(7L)).thenReturn(Optional.of(
                UserMyPagePreference.builder().userId(7L).pushEnabled(true).build()
        ));
        when(userPushTokenRepository.findByUser_IdAndActiveTrue(7L)).thenReturn(List.of(active));

        List<UserPushToken> tokens = pushTokenService.getDeliverableTokens(7L);

        assertEquals(List.of(active), tokens);
    }

    @Test
    void getDeliverableTokensReturnsEmptyWhenPushPreferenceDisabled() {
        when(userMyPagePreferenceRepository.findById(7L)).thenReturn(Optional.of(
                UserMyPagePreference.builder().userId(7L).pushEnabled(false).build()
        ));

        List<UserPushToken> tokens = pushTokenService.getDeliverableTokens(7L);

        assertTrue(tokens.isEmpty());
        verify(userPushTokenRepository, never()).findByUser_IdAndActiveTrue(7L);
    }
}
