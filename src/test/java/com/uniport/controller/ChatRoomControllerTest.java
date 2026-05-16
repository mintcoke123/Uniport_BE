package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.entity.User;
import com.uniport.service.ChatRoomService;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MatchingRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatRoomControllerTest {

    @Test
    void leaveChatRoom_convertsNumericChatRoomIdToMatchingRoomApiId() throws Exception {
        ChatRoomService chatRoomService = mock(ChatRoomService.class);
        MatchingRoomService matchingRoomService = mock(MatchingRoomService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        User currentUser = User.builder().nickname("나가는사용자").build();
        currentUser.setId(7L);

        when(currentUserResolver.resolveRequired(nullable(FirebaseAuthenticatedUser.class), eq("Bearer test-token")))
                .thenReturn(currentUser);
        when(matchingRoomService.leave("room-260", currentUser))
                .thenReturn(Map.of("success", true, "message", "Left"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatRoomController(chatRoomService, currentUserResolver, matchingRoomService)
        ).build();

        mockMvc.perform(post("/api/chat/rooms/260/leave")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Left"));

        verify(matchingRoomService).leave("room-260", currentUser);
    }
}
