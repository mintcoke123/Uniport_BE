package com.uniport.service.feedback;

import com.uniport.entity.ChatMessage;
import com.uniport.entity.GroupInvestmentFeedbackReport;
import com.uniport.entity.GroupInvestmentMemberFeedback;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.dto.StockVisualDTO;
import com.uniport.repository.GroupInvestmentFeedbackReportRepository;
import com.uniport.repository.GroupInvestmentMemberFeedbackRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import com.uniport.service.ChatService;
import com.uniport.service.PushNotificationService;
import com.uniport.service.StockVisualAssetResolver;
import com.uniport.service.TradeNewsContext;
import com.uniport.service.TradeNewsContextService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateGroupInvestmentFeedbackReportUseCaseTest {

    @Test
    void generatesPublishedNoTradeReportAndPublishesChatMessage() {
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentEndPriceProvider endPriceProvider = mock(GroupInvestmentEndPriceProvider.class);
        FeedbackCommentGenerator commentGenerator = mock(FeedbackCommentGenerator.class);
        GroupInvestmentPointSettlementService pointSettlementService = mock(GroupInvestmentPointSettlementService.class);
        StockVisualAssetResolver stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        TradeNewsContextService tradeNewsContextService = mock(TradeNewsContextService.class);
        ChatService chatService = mock(ChatService.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        GenerateGroupInvestmentFeedbackReportUseCase useCase = new GenerateGroupInvestmentFeedbackReportUseCase(
                matchingRoomRepository,
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                reportRepository,
                memberFeedbackRepository,
                endPriceProvider,
                new GroupInvestmentFeedbackCalculator(),
                new MemberDecisionFeedbackAnalyzer(),
                commentGenerator,
                pointSettlementService,
                stockVisualAssetResolver,
                tradeNewsContextService,
                chatService,
                pushNotificationService
        );
        Instant endedAt = Instant.parse("2026-01-28T13:00:00Z");
        MatchingRoom room = MatchingRoom.builder()
                .id(1L)
                .name("피드백 테스트방")
                .capacity(3)
                .memberCount(0)
                .status("ended")
                .endedAt(endedAt)
                .createdAt(Instant.parse("2026-01-21T13:00:00Z"))
                .build();
        when(matchingRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(reportRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(1L)).thenReturn(List.of(
                MatchingRoomMember.of(room, User.builder().id(10L).studentId("10").password("p").nickname("A").build()),
                MatchingRoomMember.of(room, User.builder().id(20L).studentId("20").password("p").nickname("B").build())
        ));
        when(commentGenerator.generate(any(GroupInvestmentFeedbackCalculation.class)))
                .thenReturn(new GeneratedFeedbackComment("이번 라운드는 분석할 거래가 부족해요. 다음에는 제안과 투표를 더 남겨보세요.", "TEMPLATE"));
        when(reportRepository.save(any(GroupInvestmentFeedbackReport.class))).thenAnswer(invocation -> {
            GroupInvestmentFeedbackReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(7L);
            }
            return report;
        });
        when(chatService.saveFeedbackReportMessage(eq(1L), eq(7L), anyMap()))
                .thenReturn(ChatMessage.of(1L, 0L, "시스템", "{}"));
        when(memberFeedbackRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> response = useCase.generateForRoom(1L);

        assertEquals("PUBLISHED", response.get("status"));
        assertEquals(7L, response.get("reportId"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("profitAmount")));
        verify(chatService).saveFeedbackReportMessage(eq(1L), eq(7L), anyMap());
        verify(pushNotificationService).sendGroupInvestmentFeedbackReport(
                eq(1L),
                eq(7L),
                eq("+0.0%"),
                eq(0),
                eq(0),
                eq(List.of(10L, 20L))
        );
        verify(pushNotificationService).sendGroupInvestmentPointSettlement(eq(1L), eq(7L), eq(10L), eq(0), eq(0));
        verify(pushNotificationService).sendGroupInvestmentPointSettlement(eq(1L), eq(7L), eq(20L), eq(0), eq(0));
        verify(memberFeedbackRepository).saveAll(any());
        verify(pointSettlementService).settle(any(GroupInvestmentFeedbackReport.class), any());
    }

    @Test
    void retriesPointSettlementWhenExistingReportIsPending() {
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentEndPriceProvider endPriceProvider = mock(GroupInvestmentEndPriceProvider.class);
        FeedbackCommentGenerator commentGenerator = mock(FeedbackCommentGenerator.class);
        GroupInvestmentPointSettlementService pointSettlementService = mock(GroupInvestmentPointSettlementService.class);
        StockVisualAssetResolver stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        TradeNewsContextService tradeNewsContextService = mock(TradeNewsContextService.class);
        ChatService chatService = mock(ChatService.class);
        GenerateGroupInvestmentFeedbackReportUseCase useCase = new GenerateGroupInvestmentFeedbackReportUseCase(
                matchingRoomRepository,
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                reportRepository,
                memberFeedbackRepository,
                endPriceProvider,
                new GroupInvestmentFeedbackCalculator(),
                new MemberDecisionFeedbackAnalyzer(),
                commentGenerator,
                pointSettlementService,
                stockVisualAssetResolver,
                tradeNewsContextService,
                chatService,
                mock(PushNotificationService.class)
        );
        MatchingRoom room = MatchingRoom.builder()
                .id(1L)
                .name("피드백 테스트방")
                .capacity(3)
                .memberCount(0)
                .status("ended")
                .endedAt(Instant.parse("2026-01-28T13:00:00Z"))
                .createdAt(Instant.parse("2026-01-21T13:00:00Z"))
                .build();
        GroupInvestmentFeedbackReport report = GroupInvestmentFeedbackReport.builder()
                .id(7L)
                .sessionId(1L)
                .roomId(1L)
                .status("PUBLISHED")
                .initialCapital(new BigDecimal("10000000"))
                .finalEquity(new BigDecimal("10000000"))
                .profitAmount(BigDecimal.ZERO)
                .returnRate(BigDecimal.ZERO)
                .aiComment("comment")
                .aiSource("TEMPLATE")
                .endedAt(Instant.parse("2026-01-28T13:00:00Z"))
                .generatedAt(Instant.parse("2026-01-28T13:01:00Z"))
                .pointSettlementStatus("PENDING")
                .build();

        when(matchingRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(reportRepository.findBySessionId(1L)).thenReturn(Optional.of(report));
        when(memberFeedbackRepository.findByReportOrderBySortOrderAsc(report)).thenReturn(List.of());

        Map<String, Object> response = useCase.generateForRoom(1L);

        assertEquals("PUBLISHED", response.get("status"));
        verify(pointSettlementService).settle(report, List.of());
    }

    @Test
    void responseClampsNegativeSettledPointAndExpToZero() {
        GenerateGroupInvestmentFeedbackReportUseCase useCase = new GenerateGroupInvestmentFeedbackReportUseCase(
                mock(MatchingRoomRepository.class),
                mock(VoteRepository.class),
                mock(VoteParticipantRepository.class),
                mock(MatchingRoomMemberRepository.class),
                mock(GroupInvestmentFeedbackReportRepository.class),
                mock(GroupInvestmentMemberFeedbackRepository.class),
                mock(GroupInvestmentEndPriceProvider.class),
                new GroupInvestmentFeedbackCalculator(),
                new MemberDecisionFeedbackAnalyzer(),
                mock(FeedbackCommentGenerator.class),
                mock(GroupInvestmentPointSettlementService.class),
                mock(StockVisualAssetResolver.class),
                mock(TradeNewsContextService.class),
                mock(ChatService.class),
                mock(PushNotificationService.class)
        );
        GroupInvestmentFeedbackReport report = GroupInvestmentFeedbackReport.builder()
                .id(7L)
                .sessionId(1L)
                .roomId(1L)
                .status("PUBLISHED")
                .initialCapital(new BigDecimal("10000000"))
                .finalEquity(new BigDecimal("10000000"))
                .profitAmount(BigDecimal.ZERO)
                .returnRate(BigDecimal.ZERO)
                .aiComment("comment")
                .aiSource("TEMPLATE")
                .endedAt(Instant.parse("2026-01-28T13:00:00Z"))
                .generatedAt(Instant.parse("2026-01-28T13:01:00Z"))
                .pointSettlementStatus("SETTLED")
                .build();
        GroupInvestmentMemberFeedback member = GroupInvestmentMemberFeedback.builder()
                .id(1L)
                .report(report)
                .memberId(10L)
                .nickname("A")
                .representativeDecision("삼성전자 매수 제안")
                .level("LOW")
                .contributionAmount(BigDecimal.ZERO)
                .contributionRate(new BigDecimal("-3.0"))
                .participatedDecisionCount(1)
                .totalDecisionCount(2)
                .participationRate(new BigDecimal("50.0"))
                .settledPoint(-300)
                .settledExp(-100)
                .pointSettlementStatus("SETTLED")
                .sortOrder(0)
                .build();

        Map<String, Object> response = useCase.toResponse(report, List.of(member));

        assertEquals(0, response.get("totalSettledPoint"));
        assertEquals(0, response.get("totalSettledExp"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) response.get("memberAnalyses");
        assertEquals(0, members.get(0).get("settledPoint"));
        assertEquals(0, members.get(0).get("settledExp"));
    }

    @Test
    void generatedTradeSnapshotIncludesStockVisual() {
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentEndPriceProvider endPriceProvider = mock(GroupInvestmentEndPriceProvider.class);
        FeedbackCommentGenerator commentGenerator = mock(FeedbackCommentGenerator.class);
        GroupInvestmentPointSettlementService pointSettlementService = mock(GroupInvestmentPointSettlementService.class);
        StockVisualAssetResolver stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        TradeNewsContextService tradeNewsContextService = mock(TradeNewsContextService.class);
        ChatService chatService = mock(ChatService.class);
        GenerateGroupInvestmentFeedbackReportUseCase useCase = new GenerateGroupInvestmentFeedbackReportUseCase(
                matchingRoomRepository,
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                reportRepository,
                memberFeedbackRepository,
                endPriceProvider,
                new GroupInvestmentFeedbackCalculator(),
                new MemberDecisionFeedbackAnalyzer(),
                commentGenerator,
                pointSettlementService,
                stockVisualAssetResolver,
                tradeNewsContextService,
                chatService,
                mock(PushNotificationService.class)
        );
        Instant endedAt = Instant.parse("2026-01-28T13:00:00Z");
        MatchingRoom room = MatchingRoom.builder()
                .id(1L)
                .name("피드백 테스트방")
                .capacity(3)
                .memberCount(0)
                .status("ended")
                .endedAt(endedAt)
                .createdAt(Instant.parse("2026-01-21T13:00:00Z"))
                .build();
        Vote buy = Vote.builder()
                .id(100L)
                .roomId(1L)
                .proposerId(10L)
                .proposerName("유저")
                .type("매수")
                .stockName("삼성전자")
                .stockCode("005930")
                .quantity(10)
                .proposedPrice(new BigDecimal("70000"))
                .executionPrice(new BigDecimal("70000"))
                .reason("좋은 실적")
                .createdAt(Instant.parse("2026-01-22T13:00:00Z"))
                .expiresAt(Instant.parse("2026-01-22T14:00:00Z"))
                .executedAt(Instant.parse("2026-01-22T13:30:00Z"))
                .totalMembers(3)
                .status("executed")
                .build();
        when(matchingRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(reportRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(buy));
        when(voteParticipantRepository.findByVote_IdOrderById(100L)).thenReturn(List.of());
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(1L)).thenReturn(List.of());
        when(endPriceProvider.resolveEndPrice("005930", endedAt, new BigDecimal("70000")))
                .thenReturn(new BigDecimal("71000"));
        when(commentGenerator.generate(any(GroupInvestmentFeedbackCalculation.class)))
                .thenReturn(new GeneratedFeedbackComment("삼성전자 거래가 성과에 기여했어요.", "TEMPLATE"));
        when(stockVisualAssetResolver.resolve("KRX", "005930", "삼성전자", null)).thenReturn(visual("삼성"));
        when(tradeNewsContextService.summarize("005930", "삼성전자", Instant.parse("2026-01-22T13:30:00Z")))
                .thenReturn(new TradeNewsContext(
                        1,
                        1,
                        "NEGATIVE",
                        "POSITIVE",
                        "매수 당시 관련 뉴스는 악재가 우세했지만 이후 호재 뉴스가 늘었어요.",
                        List.of("삼성전자 실적 둔화 우려"),
                        List.of("삼성전자 반등 기대 확대")
                ));
        when(reportRepository.save(any(GroupInvestmentFeedbackReport.class))).thenAnswer(invocation -> {
            GroupInvestmentFeedbackReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(7L);
            }
            return report;
        });
        when(chatService.saveFeedbackReportMessage(eq(1L), eq(7L), anyMap()))
                .thenReturn(ChatMessage.of(1L, 0L, "시스템", "{}"));

        Map<String, Object> response = useCase.generateForRoom(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> bestTrade = (Map<String, Object>) response.get("bestTrade");
        @SuppressWarnings("unchecked")
        Map<String, Object> visual = (Map<String, Object>) bestTrade.get("visual");
        @SuppressWarnings("unchecked")
        Map<String, Object> newsContext = (Map<String, Object>) bestTrade.get("newsContext");
        assertEquals("삼성", visual.get("text"));
        assertEquals("FALLBACK_SYMBOL", visual.get("type"));
        assertEquals("NEGATIVE", newsContext.get("beforeSentiment"));
        assertEquals("POSITIVE", newsContext.get("afterSentiment"));
    }

    @Test
    void generatePendingReports_endsExpiredStartedRoomAndPublishesReport() {
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentEndPriceProvider endPriceProvider = mock(GroupInvestmentEndPriceProvider.class);
        FeedbackCommentGenerator commentGenerator = mock(FeedbackCommentGenerator.class);
        GroupInvestmentPointSettlementService pointSettlementService = mock(GroupInvestmentPointSettlementService.class);
        StockVisualAssetResolver stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        TradeNewsContextService tradeNewsContextService = mock(TradeNewsContextService.class);
        ChatService chatService = mock(ChatService.class);
        GenerateGroupInvestmentFeedbackReportUseCase useCase = new GenerateGroupInvestmentFeedbackReportUseCase(
                matchingRoomRepository,
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                reportRepository,
                memberFeedbackRepository,
                endPriceProvider,
                new GroupInvestmentFeedbackCalculator(),
                new MemberDecisionFeedbackAnalyzer(),
                commentGenerator,
                pointSettlementService,
                stockVisualAssetResolver,
                tradeNewsContextService,
                chatService,
                mock(PushNotificationService.class)
        );
        MatchingRoom room = MatchingRoom.builder()
                .id(1L)
                .name("만료된 투자방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .endedAt(Instant.now().minusSeconds(60))
                .createdAt(Instant.parse("2026-01-21T13:00:00Z"))
                .build();
        when(matchingRoomRepository.findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(eq("started"), any(Instant.class)))
                .thenReturn(List.of(room));
        when(matchingRoomRepository.findByStatusAndEndedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(eq("started"), any(Instant.class)))
                .thenReturn(List.of());
        when(matchingRoomRepository.findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(eq("ended"), any(Instant.class)))
                .thenReturn(List.of());
        when(reportRepository.existsBySessionId(1L)).thenReturn(false);
        when(matchingRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(reportRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(1L)).thenReturn(List.of());
        when(commentGenerator.generate(any(GroupInvestmentFeedbackCalculation.class)))
                .thenReturn(new GeneratedFeedbackComment("이번 라운드는 분석할 거래가 부족해요.", "TEMPLATE"));
        when(reportRepository.save(any(GroupInvestmentFeedbackReport.class))).thenAnswer(invocation -> {
            GroupInvestmentFeedbackReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(7L);
            }
            return report;
        });
        when(chatService.saveFeedbackReportMessage(eq(1L), eq(7L), anyMap()))
                .thenReturn(ChatMessage.of(1L, 0L, "시스템", "{}"));

        int generated = useCase.generatePendingReports();

        assertEquals(1, generated);
        assertEquals("ended", room.getStatus());
        verify(matchingRoomRepository).save(room);
        verify(chatService).saveFeedbackReportMessage(eq(1L), eq(7L), anyMap());
    }

    @Test
    void generatePendingReports_endsLegacyStartedRoomWithoutEndTimeAfterSevenDays() {
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentEndPriceProvider endPriceProvider = mock(GroupInvestmentEndPriceProvider.class);
        FeedbackCommentGenerator commentGenerator = mock(FeedbackCommentGenerator.class);
        GroupInvestmentPointSettlementService pointSettlementService = mock(GroupInvestmentPointSettlementService.class);
        StockVisualAssetResolver stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        TradeNewsContextService tradeNewsContextService = mock(TradeNewsContextService.class);
        ChatService chatService = mock(ChatService.class);
        GenerateGroupInvestmentFeedbackReportUseCase useCase = new GenerateGroupInvestmentFeedbackReportUseCase(
                matchingRoomRepository,
                voteRepository,
                voteParticipantRepository,
                matchingRoomMemberRepository,
                reportRepository,
                memberFeedbackRepository,
                endPriceProvider,
                new GroupInvestmentFeedbackCalculator(),
                new MemberDecisionFeedbackAnalyzer(),
                commentGenerator,
                pointSettlementService,
                stockVisualAssetResolver,
                tradeNewsContextService,
                chatService,
                mock(PushNotificationService.class)
        );
        Instant createdAt = Instant.now().minus(Duration.ofDays(8));
        MatchingRoom room = MatchingRoom.builder()
                .id(2L)
                .name("레거시 투자방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(createdAt)
                .build();
        when(matchingRoomRepository.findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(eq("started"), any(Instant.class)))
                .thenReturn(List.of());
        when(matchingRoomRepository.findByStatusAndEndedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(eq("started"), any(Instant.class)))
                .thenReturn(List.of(room));
        when(matchingRoomRepository.findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(eq("ended"), any(Instant.class)))
                .thenReturn(List.of());
        when(reportRepository.existsBySessionId(2L)).thenReturn(false);
        when(matchingRoomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(reportRepository.findBySessionId(2L)).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(2L)).thenReturn(List.of());
        when(commentGenerator.generate(any(GroupInvestmentFeedbackCalculation.class)))
                .thenReturn(new GeneratedFeedbackComment("이번 라운드는 분석할 거래가 부족해요.", "TEMPLATE"));
        when(reportRepository.save(any(GroupInvestmentFeedbackReport.class))).thenAnswer(invocation -> {
            GroupInvestmentFeedbackReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(8L);
            }
            return report;
        });
        when(chatService.saveFeedbackReportMessage(eq(2L), eq(8L), anyMap()))
                .thenReturn(ChatMessage.of(2L, 0L, "시스템", "{}"));

        int generated = useCase.generatePendingReports();

        assertEquals(1, generated);
        assertEquals("ended", room.getStatus());
        assertEquals(createdAt.plus(Duration.ofDays(7)), room.getEndedAt());
        verify(matchingRoomRepository).save(room);
        verify(chatService).saveFeedbackReportMessage(eq(2L), eq(8L), anyMap());
    }

    private StockVisualDTO visual(String text) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(text)
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
    }
}
