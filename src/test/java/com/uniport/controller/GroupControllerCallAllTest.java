package com.uniport.controller;

import com.uniport.entity.ChatMessage;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.service.KisApiService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.PushNotificationService;
import com.uniport.service.StockVisualAssetResolver;
import com.uniport.service.VoteService;
import com.uniport.service.feedback.GenerateGroupInvestmentFeedbackReportUseCase;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerCallAllTest {

    @Test
    void postCallAllCreatesMentionAllMessageForRoomMember() throws Exception {
        Fixtures fixtures = new Fixtures();
        User user = User.builder()
                .studentId("20265001")
                .password("password")
                .nickname("유니포트")
                .build();
        user.setId(1L);
        ChatMessage saved = ChatMessage.of(260L, 1L, "유니포트", "{\"type\":\"mention_all\"}");
        saved.setId(123L);

        when(fixtures.authService.getUserFromTokenOrNull("Bearer test-token")).thenReturn(user);
        when(fixtures.matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(260L, 1L)).thenReturn(true);
        when(fixtures.matchingRoomMemberRepository.findByMatchingRoomIdWithUser(260L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(user).build(),
                MatchingRoomMember.builder().user(User.builder().id(2L).nickname("팀원").build()).build()
        ));
        when(fixtures.chatService.saveMentionAllMessage(260L, 1L, "유니포트")).thenReturn(saved);
        Map<String, Object> responseMessage = new HashMap<>();
        responseMessage.put("id", 123L);
        responseMessage.put("roomId", 260L);
        responseMessage.put("type", "mention_all");
        responseMessage.put("userId", 1L);
        responseMessage.put("userNickname", "유니포트");
        responseMessage.put("message", "모든 팀원을 호출했어요!");
        responseMessage.put("timestamp", "2026-05-11T12:34:56.789");
        responseMessage.put("tradeData", null);
        when(fixtures.chatService.toResponseMap(saved)).thenReturn(responseMessage);

        fixtures.mockMvc.perform(post("/api/groups/260/chat/call-all")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message.id").value(123))
                .andExpect(jsonPath("$.message.roomId").value(260))
                .andExpect(jsonPath("$.message.type").value("mention_all"))
                .andExpect(jsonPath("$.message.userId").value(1))
                .andExpect(jsonPath("$.message.userNickname").value("유니포트"))
                .andExpect(jsonPath("$.message.message").value("모든 팀원을 호출했어요!"));

        verify(fixtures.matchingRoomService).assertTeamRoomForCallAll(260L);
        verify(fixtures.chatService).saveMentionAllMessage(260L, 1L, "유니포트");
        verify(fixtures.pushNotificationService).sendChatMentionAll(260L, user, List.of(1L, 2L));
    }

    @Test
    void postCallAllReturnsUnauthorizedWhenUserIsMissing() throws Exception {
        Fixtures fixtures = new Fixtures();
        when(fixtures.authService.getUserFromTokenOrNull("")).thenReturn(null);

        fixtures.mockMvc.perform(post("/api/groups/260/chat/call-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void postCallAllReturnsForbiddenWhenUserIsNotRoomMember() throws Exception {
        Fixtures fixtures = new Fixtures();
        User user = User.builder()
                .studentId("20265002")
                .password("password")
                .nickname("외부인")
                .build();
        user.setId(2L);

        when(fixtures.authService.getUserFromTokenOrNull("Bearer test-token")).thenReturn(user);
        when(fixtures.matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(260L, 2L)).thenReturn(false);

        fixtures.mockMvc.perform(post("/api/groups/260/chat/call-all")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("해당 채팅방에 대한 접근 권한이 없습니다."));
    }

    private static class Fixtures {
        private final ChatService chatService = mock(ChatService.class);
        private final AuthService authService = mock(AuthService.class);
        private final MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        private final TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        private final TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        private final MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        private final KisApiService kisApiService = mock(KisApiService.class);
        private final VoteService voteService = mock(VoteService.class);
        private final KisWsSubscriptionManager kisWsSubscriptionManager = mock(KisWsSubscriptionManager.class);
        private final MatchingRoomService matchingRoomService = mock(MatchingRoomService.class);
        private final GenerateGroupInvestmentFeedbackReportUseCase feedbackReportUseCase = mock(GenerateGroupInvestmentFeedbackReportUseCase.class);
        private final StockVisualAssetResolver stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        private final PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(
                chatService,
                authService,
                matchingRoomMemberRepository,
                teamAccountRepository,
                teamHoldingRepository,
                matchingRoomRepository,
                kisApiService,
                voteService,
                kisWsSubscriptionManager,
                matchingRoomService,
                feedbackReportUseCase,
                stockVisualAssetResolver,
                pushNotificationService
        )).build();
    }
}
