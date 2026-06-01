package com.uniport.scheduler;

import com.uniport.service.InvestmentIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "uniport.investment-issue.refresh",
        name = "enabled",
        havingValue = "true"
)
public class InvestmentIssueRefreshScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvestmentIssueRefreshScheduler.class);

    private final InvestmentIssueService investmentIssueService;

    public InvestmentIssueRefreshScheduler(InvestmentIssueService investmentIssueService) {
        this.investmentIssueService = investmentIssueService;
    }

    @Scheduled(
            fixedDelayString = "${uniport.investment-issue.refresh.fixed-delay-ms:300000}",
            initialDelayString = "${uniport.investment-issue.refresh.initial-delay-ms:30000}"
    )
    public void refresh() {
        try {
            investmentIssueService.refreshIssueCache();
            LOGGER.info("[investment-issue-refresh] refreshed investment issue cache");
        } catch (Exception exception) {
            LOGGER.warn("[investment-issue-refresh] failed to refresh investment issue cache: {}",
                    exception.getMessage(), exception);
        }
    }
}
