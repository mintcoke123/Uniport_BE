package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.UserRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import com.uniport.service.kisws.PriceCache;
import com.uniport.websocket.GroupChatBroadcaster;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 투표 통과 조건 (agreeCount / totalMembers) > 0.5 단위 테스트.
 */
class VoteServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void isVotePassedByRatio_1인_1찬성_통과() {
        assertTrue(VoteService.isVotePassedByRatio(1, 1));
    }

    @Test
    void isVotePassedByRatio_1인_0찬성_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(0, 1));
    }

    @Test
    void isVotePassedByRatio_2인_1찬성_0점5_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(1, 2));
    }

    @Test
    void isVotePassedByRatio_2인_2찬성_통과() {
        assertTrue(VoteService.isVotePassedByRatio(2, 2));
    }

    @Test
    void isVotePassedByRatio_3인_1찬성_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(1, 3));
    }

    @Test
    void isVotePassedByRatio_3인_2찬성_통과() {
        assertTrue(VoteService.isVotePassedByRatio(2, 3));
    }

    @Test
    void isVotePassedByRatio_totalMembers_0_미통과() {
        assertFalse(VoteService.isVotePassedByRatio(0, 0));
        assertFalse(VoteService.isVotePassedByRatio(1, 0));
    }

    @Test
    void createVote_broadcastsVoteUpdateAfterSavingProposerVote() throws Exception {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        ChatService chatService = mock(ChatService.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        VoteService service = new VoteService(
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                null,
                null,
                null,
                null,
                null,
                chatService,
                broadcaster,
                null,
                null,
                pushNotificationService
        );
        User proposer = User.builder()
                .id(7L)
                .nickname("제안자")
                .studentId("20260001")
                .password("pw")
                .build();

        when(chatService.hasFeedbackMessage(123L)).thenReturn(false);
        when(voteRepository.findByRoomIdAndStatusOrderByCreatedAtDesc(123L, "ongoing")).thenReturn(List.of());
        when(matchingRoomMemberRepository.countByMatchingRoomId(123L)).thenReturn(2L);
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(123L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(proposer).build(),
                MatchingRoomMember.builder().user(User.builder().id(8L).nickname("팀원").build()).build()
        ));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> {
            Vote vote = invocation.getArgument(0);
            vote.setId(456L);
            return vote;
        });
        when(voteParticipantRepository.save(any(VoteParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createVote(
                123L,
                proposer,
                "매수",
                "삼성전자",
                "005930",
                3,
                BigDecimal.valueOf(70000),
                "좋아 보여요",
                VoteService.ORDER_STRATEGY_MARKET,
                null,
                null,
                null
        );

        InOrder inOrder = inOrder(voteParticipantRepository, broadcaster);
        inOrder.verify(voteParticipantRepository).save(any(VoteParticipant.class));

        ArgumentCaptor<String> groupIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        inOrder.verify(broadcaster).broadcast(groupIdCaptor.capture(), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = OBJECT_MAPPER.readValue(payloadCaptor.getValue(), Map.class);
        assertEquals("123", groupIdCaptor.getValue());
        assertEquals("vote_update", payload.get("type"));
        assertEquals(123L, ((Number) payload.get("groupId")).longValue());
        assertEquals(456L, ((Number) payload.get("voteId")).longValue());
        verify(voteParticipantRepository).save(any(VoteParticipant.class));
        verify(pushNotificationService).sendVoteCreated(any(Long.class), any(Vote.class), any(List.class));
    }

    @Test
    void createVoteRejectsInvalidOrderFieldsBeforeSaving() {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        ChatService chatService = mock(ChatService.class);
        VoteService service = new VoteService(
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                null,
                null,
                null,
                null,
                null,
                chatService,
                null,
                null,
                null,
                null
        );
        User proposer = User.builder().id(7L).nickname("제안자").build();

        ApiException quantity = assertThrows(ApiException.class, () -> service.createVote(
                123L,
                proposer,
                "매수",
                "삼성전자",
                "005930",
                0,
                BigDecimal.valueOf(70000),
                "좋아 보여요",
                VoteService.ORDER_STRATEGY_MARKET,
                null,
                null,
                null
        ));
        assertEquals("quantity must be positive", quantity.getMessage());

        ApiException stockCode = assertThrows(ApiException.class, () -> service.createVote(
                123L,
                proposer,
                "매수",
                "삼성전자",
                " ",
                3,
                BigDecimal.valueOf(70000),
                "좋아 보여요",
                VoteService.ORDER_STRATEGY_MARKET,
                null,
                null,
                null
        ));
        assertEquals("stockCode is required", stockCode.getMessage());

        ApiException proposedPrice = assertThrows(ApiException.class, () -> service.createVote(
                123L,
                proposer,
                "매수",
                "삼성전자",
                "005930",
                3,
                BigDecimal.ZERO,
                "좋아 보여요",
                VoteService.ORDER_STRATEGY_MARKET,
                null,
                null,
                null
        ));
        assertEquals("proposedPrice must be positive", proposedPrice.getMessage());
        verifyNoInteractions(voteRepository, voteParticipantRepository, matchingRoomMemberRepository, chatService);
    }

    @Test
    void submitVoteSendsVoteClosedPushWhenMajorityRejectsVote() {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        ChatService chatService = mock(ChatService.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        VoteService service = new VoteService(
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                null,
                null,
                null,
                null,
                null,
                chatService,
                broadcaster,
                null,
                null,
                pushNotificationService
        );
        Vote vote = Vote.builder()
                .id(456L)
                .roomId(123L)
                .proposerId(7L)
                .type("매수")
                .stockName("삼성전자")
                .totalMembers(2)
                .status("ongoing")
                .build();
        User voter = User.builder().id(8L).nickname("반대자").build();
        when(chatService.hasFeedbackMessage(123L)).thenReturn(false);
        when(voteRepository.findById(456L)).thenReturn(Optional.of(vote));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(123L, 8L)).thenReturn(true);
        when(voteParticipantRepository.findByVote_IdAndUserId(456L, 8L)).thenReturn(Optional.empty());
        when(voteParticipantRepository.findByVote_IdOrderById(456L)).thenReturn(List.of(
                VoteParticipant.builder().vote(vote).userId(7L).voteChoice("반대").build(),
                VoteParticipant.builder().vote(vote).userId(8L).voteChoice("반대").build()
        ));
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(123L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(User.builder().id(7L).build()).build(),
                MatchingRoomMember.builder().user(voter).build()
        ));

        service.submitVote(123L, 456L, voter, "반대");

        verify(pushNotificationService).sendVoteClosed(123L, vote, List.of(7L, 8L));
    }

    @Test
    void processExpiredOngoingVotesSendsVoteClosedPush() {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        ChatService chatService = mock(ChatService.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        VoteService service = new VoteService(
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                null,
                null,
                null,
                null,
                null,
                chatService,
                broadcaster,
                null,
                null,
                pushNotificationService
        );
        Vote vote = Vote.builder()
                .id(456L)
                .roomId(123L)
                .type("매수")
                .stockName("삼성전자")
                .status("ongoing")
                .expiresAt(Instant.now().minusSeconds(1))
                .build();
        when(voteRepository.findByStatus("ongoing")).thenReturn(List.of(vote));
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(123L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(User.builder().id(7L).build()).build(),
                MatchingRoomMember.builder().user(User.builder().id(8L).build()).build()
        ));

        service.processExpiredOngoingVotes();

        verify(pushNotificationService).sendVoteClosed(123L, vote, List.of(7L, 8L));
    }

    @Test
    void processPendingVotesSendsTradeExecutedPushWhenConditionExecutes() {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        TradeService tradeService = mock(TradeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PriceCache priceCache = mock(PriceCache.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatService chatService = mock(ChatService.class);
        GroupChatBroadcaster broadcaster = mock(GroupChatBroadcaster.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        VoteService service = new VoteService(
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                null,
                tradeService,
                userRepository,
                priceCache,
                kisApiService,
                chatService,
                broadcaster,
                null,
                null,
                pushNotificationService
        );
        Vote vote = Vote.builder()
                .id(456L)
                .roomId(123L)
                .proposerId(7L)
                .type("매수")
                .stockName("삼성전자")
                .stockCode("005930")
                .quantity(3)
                .proposedPrice(BigDecimal.valueOf(70000))
                .orderStrategy(VoteService.ORDER_STRATEGY_MARKET)
                .status(VoteService.STATUS_PENDING)
                .executionExpiresAt(Instant.now().plusSeconds(60))
                .build();
        User proposer = User.builder().id(7L).nickname("제안자").build();
        when(voteRepository.findByStatus(VoteService.STATUS_PENDING)).thenReturn(List.of(vote));
        when(voteRepository.findByIdForUpdate(456L)).thenReturn(Optional.of(vote));
        when(userRepository.findById(7L)).thenReturn(Optional.of(proposer));
        when(priceCache.get("005930")).thenReturn(Optional.empty());
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(123L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(proposer).build(),
                MatchingRoomMember.builder().user(User.builder().id(8L).build()).build()
        ));

        service.processPendingVotes();

        verify(pushNotificationService).sendTradeExecuted(123L, vote, List.of(7L, 8L));
    }
}
