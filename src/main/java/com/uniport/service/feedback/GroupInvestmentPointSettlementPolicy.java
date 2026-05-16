package com.uniport.service.feedback;

import com.uniport.entity.GroupInvestmentMemberFeedback;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class GroupInvestmentPointSettlementPolicy {

    private static final BigDecimal CONTRIBUTION_POINT_PER_PERCENT = BigDecimal.valueOf(100);
    private static final BigDecimal PARTICIPATION_POINT_PER_PERCENT = BigDecimal.valueOf(2);

    public int calculatePoint(GroupInvestmentMemberFeedback memberFeedback) {
        if (memberFeedback == null) {
            return 0;
        }
        BigDecimal contributionPoint = zeroIfNull(memberFeedback.getContributionRate())
                .multiply(CONTRIBUTION_POINT_PER_PERCENT);
        BigDecimal participationPoint = zeroIfNull(memberFeedback.getParticipationRate())
                .multiply(PARTICIPATION_POINT_PER_PERCENT);
        return contributionPoint.add(participationPoint)
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    public int calculateExp(GroupInvestmentMemberFeedback memberFeedback) {
        if (memberFeedback == null) {
            return 0;
        }
        BigDecimal positiveContribution = zeroIfNull(memberFeedback.getContributionRate()).max(BigDecimal.ZERO)
                .multiply(CONTRIBUTION_POINT_PER_PERCENT);
        BigDecimal participationExp = zeroIfNull(memberFeedback.getParticipationRate())
                .multiply(PARTICIPATION_POINT_PER_PERCENT);
        return positiveContribution.add(participationExp)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
