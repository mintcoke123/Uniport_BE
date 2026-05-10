package com.uniport.service.feedback;

import com.uniport.entity.GroupInvestmentFeedbackReport;
import com.uniport.entity.GroupInvestmentMemberFeedback;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.User;
import com.uniport.repository.GroupInvestmentFeedbackReportRepository;
import com.uniport.repository.GroupInvestmentMemberFeedbackRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.PointLedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupInvestmentPointSettlementServiceTest {

    @Test
    void settlesPositiveContributionPointsOncePerMemberFeedback() {
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PointLedgerService pointLedgerService = mock(PointLedgerService.class);
        GroupInvestmentPointSettlementService service = new GroupInvestmentPointSettlementService(
                memberFeedbackRepository,
                reportRepository,
                userRepository,
                pointLedgerService,
                new GroupInvestmentPointSettlementPolicy()
        );
        GroupInvestmentFeedbackReport report = report();
        GroupInvestmentMemberFeedback member = memberFeedback(report, 1L, 10L, "8.0");
        User user = User.builder().id(10L).studentId("10").password("p").nickname("A").build();
        PointTransaction transaction = PointTransaction.builder().amount(900).balanceAfter(1900).build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(pointLedgerService.earn(user, 900, "GROUP_FEEDBACK_REPORT", "member-feedback-1", "피드백 리포트 정산"))
                .thenReturn(transaction);
        when(memberFeedbackRepository.save(any(GroupInvestmentMemberFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupInvestmentPointSettlementResult result = service.settle(report, List.of(member));

        assertEquals("SETTLED", report.getPointSettlementStatus());
        assertEquals(900, result.totalSettledPoint());
        assertEquals(900, member.getSettledPoint());
        assertEquals("SETTLED", member.getPointSettlementStatus());
        verify(memberFeedbackRepository).save(member);
        verify(reportRepository).save(report);
    }

    @Test
    void deductsPointsForNegativeContributionAfterParticipationBonus() {
        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository = mock(GroupInvestmentMemberFeedbackRepository.class);
        GroupInvestmentFeedbackReportRepository reportRepository = mock(GroupInvestmentFeedbackReportRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PointLedgerService pointLedgerService = mock(PointLedgerService.class);
        GroupInvestmentPointSettlementService service = new GroupInvestmentPointSettlementService(
                memberFeedbackRepository,
                reportRepository,
                userRepository,
                pointLedgerService,
                new GroupInvestmentPointSettlementPolicy()
        );
        GroupInvestmentFeedbackReport report = report();
        GroupInvestmentMemberFeedback member = memberFeedback(report, 1L, 10L, "-3.0");
        User user = User.builder().id(10L).studentId("10").password("p").nickname("A").build();
        PointTransaction transaction = PointTransaction.builder().amount(-200).balanceAfter(300).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(pointLedgerService.deduct(user, 200, "GROUP_FEEDBACK_REPORT", "member-feedback-1", "피드백 리포트 정산"))
                .thenReturn(transaction);
        when(memberFeedbackRepository.save(any(GroupInvestmentMemberFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupInvestmentPointSettlementResult result = service.settle(report, List.of(member));

        assertEquals(-200, result.totalSettledPoint());
        assertEquals(-200, member.getSettledPoint());
        assertEquals("SETTLED", member.getPointSettlementStatus());
    }

    private GroupInvestmentFeedbackReport report() {
        return GroupInvestmentFeedbackReport.builder()
                .id(7L)
                .sessionId(1L)
                .roomId(1L)
                .status("PUBLISHED")
                .initialCapital(new BigDecimal("10000000"))
                .finalEquity(new BigDecimal("10800000"))
                .profitAmount(new BigDecimal("800000"))
                .returnRate(new BigDecimal("8.0"))
                .aiComment("comment")
                .aiSource("TEMPLATE")
                .endedAt(Instant.parse("2026-01-28T13:00:00Z"))
                .generatedAt(Instant.parse("2026-01-28T13:01:00Z"))
                .build();
    }

    private GroupInvestmentMemberFeedback memberFeedback(GroupInvestmentFeedbackReport report,
                                                         Long id,
                                                         Long memberId,
                                                         String contributionRate) {
        return GroupInvestmentMemberFeedback.builder()
                .id(id)
                .report(report)
                .memberId(memberId)
                .nickname("A")
                .representativeDecision("삼성전자 매수 제안")
                .level("HIGH")
                .contributionAmount(new BigDecimal("800000"))
                .contributionRate(new BigDecimal(contributionRate))
                .participatedDecisionCount(1)
                .totalDecisionCount(2)
                .participationRate(new BigDecimal("50.0"))
                .sortOrder(0)
                .build();
    }
}
