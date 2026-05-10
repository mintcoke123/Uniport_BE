package com.uniport.service.feedback;

import com.uniport.entity.GroupInvestmentFeedbackReport;
import com.uniport.entity.GroupInvestmentMemberFeedback;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.GroupInvestmentFeedbackReportRepository;
import com.uniport.repository.GroupInvestmentMemberFeedbackRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.PointLedgerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GroupInvestmentPointSettlementService {

    private static final String SOURCE_TYPE = "GROUP_FEEDBACK_REPORT";
    private static final String DESCRIPTION = "피드백 리포트 정산";

    private final GroupInvestmentMemberFeedbackRepository memberFeedbackRepository;
    private final GroupInvestmentFeedbackReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PointLedgerService pointLedgerService;
    private final GroupInvestmentPointSettlementPolicy settlementPolicy;

    public GroupInvestmentPointSettlementService(GroupInvestmentMemberFeedbackRepository memberFeedbackRepository,
                                                 GroupInvestmentFeedbackReportRepository reportRepository,
                                                 UserRepository userRepository,
                                                 PointLedgerService pointLedgerService,
                                                 GroupInvestmentPointSettlementPolicy settlementPolicy) {
        this.memberFeedbackRepository = memberFeedbackRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.pointLedgerService = pointLedgerService;
        this.settlementPolicy = settlementPolicy;
    }

    @Transactional
    public GroupInvestmentPointSettlementResult settle(GroupInvestmentFeedbackReport report,
                                                       List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        int settled = 0;
        int skipped = 0;
        int totalPoint = 0;
        Instant settledAt = Instant.now();

        List<GroupInvestmentMemberFeedback> safeMemberFeedbacks = memberFeedbacks != null ? memberFeedbacks : List.of();
        for (GroupInvestmentMemberFeedback memberFeedback : safeMemberFeedbacks) {
            if (memberFeedback == null) {
                continue;
            }
            String currentStatus = memberFeedback.getPointSettlementStatus();
            if ("SETTLED".equals(currentStatus)) {
                settled++;
                totalPoint += safePoint(memberFeedback);
                continue;
            }
            if ("SKIPPED".equals(currentStatus)) {
                skipped++;
                continue;
            }

            int point = settlementPolicy.calculatePoint(memberFeedback);
            if (point == 0) {
                markSkipped(memberFeedback, settledAt);
                skipped++;
                continue;
            }

            PointTransaction transaction = settleMember(memberFeedback, point);
            memberFeedback.setSettledPoint(point);
            memberFeedback.setPointTransactionId(transaction.getId() != null
                    ? String.valueOf(transaction.getId())
                    : sourceId(memberFeedback));
            memberFeedback.setPointSettlementStatus("SETTLED");
            memberFeedback.setPointSettledAt(settledAt);
            memberFeedbackRepository.save(memberFeedback);
            settled++;
            totalPoint += point;
        }

        if (report != null) {
            report.setPointSettlementStatus("SETTLED");
            report.setPointSettledAt(settledAt);
            reportRepository.save(report);
        }
        return new GroupInvestmentPointSettlementResult(settled, skipped, totalPoint);
    }

    private PointTransaction settleMember(GroupInvestmentMemberFeedback memberFeedback, int point) {
        User user = userRepository.findById(memberFeedback.getMemberId())
                .orElseThrow(() -> new ApiException("member user not found", HttpStatus.NOT_FOUND));
        if (point > 0) {
            return pointLedgerService.earn(user, point, SOURCE_TYPE, sourceId(memberFeedback), DESCRIPTION);
        }
        return pointLedgerService.deduct(user, Math.abs(point), SOURCE_TYPE, sourceId(memberFeedback), DESCRIPTION);
    }

    private void markSkipped(GroupInvestmentMemberFeedback memberFeedback, Instant settledAt) {
        memberFeedback.setSettledPoint(0);
        memberFeedback.setPointSettlementStatus("SKIPPED");
        memberFeedback.setPointSettledAt(settledAt);
        memberFeedbackRepository.save(memberFeedback);
    }

    private String sourceId(GroupInvestmentMemberFeedback memberFeedback) {
        if (memberFeedback.getId() == null) {
            throw new IllegalArgumentException("memberFeedback id is required for point settlement");
        }
        return "member-feedback-" + memberFeedback.getId();
    }

    private int safePoint(GroupInvestmentMemberFeedback memberFeedback) {
        return memberFeedback.getSettledPoint() != null ? memberFeedback.getSettledPoint() : 0;
    }
}
