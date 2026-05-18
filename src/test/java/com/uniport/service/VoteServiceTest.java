package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void createVoteRejectsEndedRoomBeforeSaving() {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatService chatService = mock(ChatService.class);
        VoteService service = new VoteService(
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                matchingRoomRepository,
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
        MatchingRoom endedRoom = MatchingRoom.builder()
                .id(123L)
                .capacity(3)
                .status("ended")
                .build();
        when(matchingRoomRepository.findById(123L)).thenReturn(Optional.of(endedRoom));

        ApiException exception = assertThrows(ApiException.class, () -> service.createVote(
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
        ));

        assertEquals("종료된 채팅방은 보기만 할 수 있습니다.", exception.getMessage());
        verify(voteRepository, never()).save(any(Vote.class));
        verifyNoInteractions(voteParticipantRepository, matchingRoomMemberRepository, chatService);
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
    void submitVoteSchedulesMarketOrderWhenMajorityPassesOutsideTradingHours() {
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
                .totalMembers(2)
                .status("ongoing")
                .build();
        User voter = User.builder().id(8L).nickname("찬성자").build();
        when(chatService.hasFeedbackMessage(123L)).thenReturn(false);
        when(voteRepository.findById(456L)).thenReturn(Optional.of(vote));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(123L, 8L)).thenReturn(true);
        when(voteParticipantRepository.findByVote_IdAndUserId(456L, 8L)).thenReturn(Optional.empty());
        when(voteParticipantRepository.findByVote_IdOrderById(456L)).thenReturn(List.of(
                VoteParticipant.builder().vote(vote).userId(7L).voteChoice("찬성").build(),
                VoteParticipant.builder().vote(vote).userId(8L).voteChoice("찬성").build()
        ));
        when(tradeService.isTradingHoursNow()).thenReturn(false);
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(123L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(User.builder().id(7L).build()).build(),
                MatchingRoomMember.builder().user(voter).build()
        ));

        Map<String, Object> response = service.submitVote(123L, 456L, voter, "찬성");

        assertEquals(VoteService.STATUS_PENDING, vote.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> voteSummary = (Map<String, Object>) response.get("vote");
        assertEquals(VoteService.STATUS_PENDING, voteSummary.get("status"));
        verify(tradeService).isTradingHoursNow();
        verify(tradeService, never()).placeOrderForTeam(any(), any(), any());
        verifyNoInteractions(userRepository, priceCache, kisApiService);
    }

    @Test
    void processPendingVotesSkipsEndedRooms() {
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
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
                matchingRoomRepository,
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
        MatchingRoom endedRoom = MatchingRoom.builder()
                .id(123L)
                .capacity(3)
                .status("ended")
                .build();
        when(voteRepository.findByStatus(VoteService.STATUS_PENDING)).thenReturn(List.of(vote));
        when(voteRepository.findById(456L)).thenReturn(Optional.of(vote));
        when(matchingRoomRepository.findById(123L)).thenReturn(Optional.of(endedRoom));

        service.processPendingVotes();

        verify(voteRepository, never()).findByIdForUpdate(456L);
        verifyNoInteractions(tradeService, userRepository, priceCache, kisApiService, chatService, pushNotificationService);
    }

    @Test
    void processPendingVotesSkipsMarketOrdersOutsideTradingHours() {
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
                .build();
        when(voteRepository.findByStatus(VoteService.STATUS_PENDING)).thenReturn(List.of(vote));
        when(voteRepository.findById(456L)).thenReturn(Optional.of(vote));
        when(tradeService.isTradingHoursNow()).thenReturn(false);

        service.processPendingVotes();

        verify(tradeService).isTradingHoursNow();
        verify(voteRepository, never()).findByIdForUpdate(456L);
        verify(tradeService, never()).placeOrderForTeam(any(), any(), any());
        verifyNoInteractions(userRepository, priceCache, kisApiService, chatService, pushNotificationService);
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
        when(voteRepository.findById(456L)).thenReturn(Optional.of(vote));
        when(tradeService.isTradingHoursNow()).thenReturn(true);
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

    @Test
    void processPendingVotesContinuesWhenOnePendingVoteFails() {
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
        Vote failingVote = Vote.builder()
                .id(300L)
                .roomId(329L)
                .proposerId(489L)
                .type("매수")
                .stockName("KODEX 200선물인버스2X")
                .stockCode("252670")
                .quantity(999999)
                .proposedPrice(BigDecimal.valueOf(118))
                .orderStrategy(VoteService.ORDER_STRATEGY_MARKET)
                .status(VoteService.STATUS_PENDING)
                .build();
        Vote executableVote = Vote.builder()
                .id(313L)
                .roomId(334L)
                .proposerId(487L)
                .type("매수")
                .stockName("웨이브테크")
                .stockCode("999999")
                .quantity(2)
                .proposedPrice(BigDecimal.valueOf(900000))
                .orderStrategy(VoteService.ORDER_STRATEGY_LIMIT)
                .limitPrice(BigDecimal.valueOf(900000))
                .status(VoteService.STATUS_PENDING)
                .executionExpiresAt(Instant.now().plusSeconds(60))
                .build();
        User proposer = User.builder().id(487L).nickname("apple").build();
        when(voteRepository.findByStatus(VoteService.STATUS_PENDING)).thenReturn(List.of(failingVote, executableVote));
        when(voteRepository.findById(300L)).thenReturn(Optional.of(failingVote));
        when(voteRepository.findById(313L)).thenReturn(Optional.of(executableVote));
        when(voteRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(failingVote));
        when(voteRepository.findByIdForUpdate(313L)).thenReturn(Optional.of(executableVote));
        when(tradeService.isTradingHoursNow()).thenReturn(true);
        when(userRepository.findById(489L)).thenReturn(Optional.of(User.builder().id(489L).nickname("kakao").build()));
        when(userRepository.findById(487L)).thenReturn(Optional.of(proposer));
        when(priceCache.get("252670")).thenReturn(Optional.empty());
        when(priceCache.get("999999")).thenReturn(Optional.empty());
        when(tradeService.placeOrderForTeam(any(PlaceOrderRequestDTO.class), any(Long.class), any(User.class)))
                .thenAnswer(invocation -> {
                    Long teamId = invocation.getArgument(1);
                    if (Long.valueOf(329L).equals(teamId)) {
                        throw new ApiException("팀 잔액이 부족합니다.", org.springframework.http.HttpStatus.BAD_REQUEST);
                    }
                    return null;
                });
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(334L)).thenReturn(List.of(
                MatchingRoomMember.builder().user(proposer).build(),
                MatchingRoomMember.builder().user(User.builder().id(489L).nickname("kakao").build()).build()
        ));

        assertDoesNotThrow(service::processPendingVotes);

        assertEquals(VoteService.STATUS_PENDING, failingVote.getStatus());
        assertEquals(VoteService.STATUS_EXECUTED, executableVote.getStatus());
        verify(pushNotificationService).sendTradeExecuted(334L, executableVote, List.of(487L, 489L));
    }
}
