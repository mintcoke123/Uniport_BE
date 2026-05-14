package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.PushTokenRegisterRequestDTO;
import com.uniport.dto.PushTokenResponseDTO;
import com.uniport.dto.PushTokenUnregisterRequestDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.PushTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushTokenControllerTest {

    @Test
    void registerTokenReturnsTokenStatusAndDelegatesToService() throws Exception {
        PushTokenService pushTokenService = mock(PushTokenService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder().id(7L).nickname("push-user").build();
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        when(pushTokenService.registerToken(eq(currentUser), any(PushTokenRegisterRequestDTO.class)))
                .thenReturn(PushTokenResponseDTO.builder()
                        .id(1L)
                        .platform("android")
                        .permissionStatus("granted")
                        .active(true)
                        .lastSeenAt("2026-05-14T09:00:00Z")
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PushTokenController(pushTokenService, currentUserResolver))
                .build();

        mockMvc.perform(post("/api/users/me/push-tokens")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "fcm_registration_token",
                                  "platform": "android",
                                  "permissionStatus": "granted"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.platform").value("android"))
                .andExpect(jsonPath("$.permissionStatus").value("granted"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lastSeenAt").value("2026-05-14T09:00:00Z"));

        verify(pushTokenService).registerToken(eq(currentUser), any(PushTokenRegisterRequestDTO.class));
    }

    @Test
    void unregisterTokenReturnsNoContentAndDelegatesToService() throws Exception {
        PushTokenService pushTokenService = mock(PushTokenService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder().id(7L).nickname("push-user").build();
        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PushTokenController(pushTokenService, currentUserResolver))
                .build();

        mockMvc.perform(post("/api/users/me/push-tokens/unregister")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "fcm_registration_token"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(pushTokenService).unregisterToken(eq(currentUser), any(PushTokenUnregisterRequestDTO.class));
    }
}
