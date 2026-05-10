package com.uniport.scheduler;

import com.uniport.service.feedback.GenerateGroupInvestmentFeedbackReportUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GroupInvestmentFeedbackReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupInvestmentFeedbackReportScheduler.class);

    private final GenerateGroupInvestmentFeedbackReportUseCase useCase;

    public GroupInvestmentFeedbackReportScheduler(GenerateGroupInvestmentFeedbackReportUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${group.feedback-report.job.fixed-delay-ms:300000}")
    public void run() {
        try {
            int generated = useCase.generatePendingReports();
            if (generated > 0) {
                log.info("[group-feedback-report] generated={}", generated);
            }
        } catch (Exception e) {
            log.warn("[group-feedback-report] job failed: {}", e.getMessage());
        }
    }
}
