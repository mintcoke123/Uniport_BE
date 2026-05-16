package com.uniport.service.feedback;

import com.uniport.entity.GroupInvestmentFeedbackReport;
import com.uniport.entity.GroupInvestmentMemberFeedback;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.GroupInvestmentFeedbackReportRepository;
import com.uniport.repository.GroupInvestmentMemberFeedbackRepository;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.LearningProgressPolicy;
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
    private final LearningUserStateRepository learningUserStateRepository;
    private final PointLedgerService pointLedgerService;
    private final GroupInvestmentPointSettlementPolicy settlementPolicy;

    public GroupInvestmentPointSettlementService(GroupInvestmentMemberFeedbackRepository memberFeedbackRepository,
                                                 GroupInvestmentFeedbackReportRepository reportRepository,
                                                 UserRepository userRepository,
                                                 LearningUserStateRepository learningUserStateRepository,
                                                 PointLedgerService pointLedgerService,
                                                 GroupInvestmentPointSettlementPolicy settlementPolicy) {
        this.memberFeedbackRepository = memberFeedbackRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.learningUserStateRepository = learningUserStateRepository;
        this.pointLedgerService = pointLedgerService;
        this.settlementPolicy = settlementPolicy;
    }

    @Transactional
    public GroupInvestmentPointSettlementResult settle(GroupInvestmentFeedbackReport report,
                                                       List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        int settled = 0;
        int skipped = 0;
        int totalPoint = 0;
        int totalExp = 0;
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
                totalExp += safeExp(memberFeedback);
                continue;
            }
            if ("SKIPPED".equals(currentStatus)) {
                skipped++;
                continue;
            }

            int point = Math.max(settlementPolicy.calculatePoint(memberFeedback), 0);
            int exp = Math.max(settlementPolicy.calculateExp(memberFeedback), 0);
            if (point == 0 && exp == 0) {
                markSkipped(memberFeedback, settledAt);
                skipped++;
                continue;
            }

            User user = userRepository.findById(memberFeedback.getMemberId())
                    .orElseThrow(() -> new ApiException("member user not found", HttpStatus.NOT_FOUND));
            PointTransaction transaction = point != 0 ? settlePoint(memberFeedback, user, point) : null;
            settleExperience(user, exp);
            memberFeedback.setSettledPoint(point);
            memberFeedback.setSettledExp(exp);
            memberFeedback.setPointTransactionId(transaction != null && transaction.getId() != null
                    ? String.valueOf(transaction.getId())
                    : point != 0 ? sourceId(memberFeedback) : null);
            memberFeedback.setPointSettlementStatus("SETTLED");
            memberFeedback.setPointSettledAt(settledAt);
            memberFeedbackRepository.save(memberFeedback);
            settled++;
            totalPoint += point;
            totalExp += exp;
        }

        if (report != null) {
            report.setPointSettlementStatus("SETTLED");
            report.setPointSettledAt(settledAt);
            reportRepository.save(report);
        }
        return new GroupInvestmentPointSettlementResult(settled, skipped, totalPoint, totalExp);
    }

    private PointTransaction settlePoint(GroupInvestmentMemberFeedback memberFeedback, User user, int point) {
        if (point > 0) {
            return pointLedgerService.earn(user, point, SOURCE_TYPE, sourceId(memberFeedback), DESCRIPTION);
        }
        return null;
    }

    private void settleExperience(User user, int exp) {
        if (user == null || user.getId() == null || exp <= 0) {
            return;
        }
        LearningUserStateEntity state = learningUserStateRepository.findById(user.getId())
                .orElseGet(() -> newLearningState(user.getId()));
        int nextTotalExp = safeInt(state.getExp()) + exp;
        LearningProgressPolicy.Progress progress = LearningProgressPolicy.fromExp(nextTotalExp);
        state.setExp(progress.totalExp());
        state.setLevel(progress.level());
        learningUserStateRepository.save(state);
    }

    private void markSkipped(GroupInvestmentMemberFeedback memberFeedback, Instant settledAt) {
        memberFeedback.setSettledPoint(0);
        memberFeedback.setSettledExp(0);
        memberFeedback.setPointSettlementStatus("SKIPPED");
        memberFeedback.setPointSettledAt(settledAt);
        memberFeedbackRepository.save(memberFeedback);
    }

    private LearningUserStateEntity newLearningState(Long userId) {
        return LearningUserStateEntity.builder()
                .userId(userId)
                .level(1)
                .point(0)
                .exp(0)
                .streakDays(0)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .submittedStepIdsJson("[]")
                .educationCurrentDayJson("{}")
                .educationCompletedDaysJson("{}")
                .educationQuizAnswersJson("{}")
                .educationCardProgressJson("{}")
                .educationSectorSelectionsJson("{}")
                .build();
    }

    private String sourceId(GroupInvestmentMemberFeedback memberFeedback) {
        if (memberFeedback.getId() == null) {
            throw new IllegalArgumentException("memberFeedback id is required for point settlement");
        }
        return "member-feedback-" + memberFeedback.getId();
    }

    private int safePoint(GroupInvestmentMemberFeedback memberFeedback) {
        return Math.max(memberFeedback.getSettledPoint() != null ? memberFeedback.getSettledPoint() : 0, 0);
    }

    private int safeExp(GroupInvestmentMemberFeedback memberFeedback) {
        return Math.max(memberFeedback.getSettledExp() != null ? memberFeedback.getSettledExp() : 0, 0);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}
