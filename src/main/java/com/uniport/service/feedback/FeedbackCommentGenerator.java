package com.uniport.service.feedback;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FeedbackCommentGenerator {

    private static final int MAX_LENGTH = 90;
    private static final List<String> PROHIBITED_WORDS = List.of(
            "무조건", "반드시", "추천", "확실히 오른다", "실패했다", "잘못했다", "책임"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d[\\d,]*(?:\\.\\d+)?(?:%|원)?");

    private final GroupFeedbackLlmClient llmClient;

    @Autowired
    public FeedbackCommentGenerator(ObjectProvider<GroupFeedbackLlmClient> llmClientProvider) {
        this(llmClientProvider.getIfAvailable(() -> facts -> Optional.empty()));
    }

    public FeedbackCommentGenerator(GroupFeedbackLlmClient llmClient) {
        this.llmClient = llmClient != null ? llmClient : facts -> Optional.empty();
    }

    public GeneratedFeedbackComment generate(GroupInvestmentFeedbackCalculation calculation) {
        GroupFeedbackFacts facts = new GroupFeedbackFacts(
                calculation.returnRate(),
                calculation.finalEquity(),
                calculation.bestTrade().orElse(null),
                calculation.worstTrade().orElse(null),
                "친근하지만 과장 없는 투자 학습 피드백",
                MAX_LENGTH
        );
        Optional<String> generated = llmClient.generate(facts);
        if (generated.isPresent() && isValid(generated.get(), facts)) {
            return new GeneratedFeedbackComment(generated.get().trim(), "LLM");
        }
        return new GeneratedFeedbackComment(fallback(facts), "TEMPLATE");
    }

    private boolean isValid(String comment, GroupFeedbackFacts facts) {
        if (comment == null) {
            return false;
        }
        String value = comment.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            return false;
        }
        if (PROHIBITED_WORDS.stream().anyMatch(value::contains)) {
            return false;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        while (matcher.find()) {
            String number = matcher.group();
            if (!allowedNumber(number, facts)) {
                return false;
            }
        }
        return true;
    }

    private boolean allowedNumber(String number, GroupFeedbackFacts facts) {
        String normalized = number.replace(",", "");
        return matches(normalized, facts.returnRate())
                || matches(normalized, facts.finalEquity())
                || (facts.bestTrade() != null && matches(normalized, facts.bestTrade().pnlAmount()))
                || (facts.worstTrade() != null && matches(normalized, facts.worstTrade().pnlAmount()));
    }

    private boolean matches(String text, BigDecimal value) {
        if (value == null) {
            return false;
        }
        String numeric = text.replace("%", "").replace("원", "");
        return numeric.equals(value.stripTrailingZeros().toPlainString())
                || numeric.equals(value.setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
                || numeric.equals(value.setScale(0, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
    }

    private String fallback(GroupFeedbackFacts facts) {
        TradePnlSnapshot best = facts.bestTrade();
        TradePnlSnapshot worst = facts.worstTrade();
        boolean hasProfit = best != null && best.pnlAmount().compareTo(BigDecimal.ZERO) > 0;
        boolean hasLoss = worst != null && worst.pnlAmount().compareTo(BigDecimal.ZERO) < 0;
        if (hasProfit && hasLoss) {
            return stockName(best) + "에서는 성과에 기여한 점이 좋았지만, "
                    + stockName(worst) + "에서는 리스크 관리가 아쉬웠어요.";
        }
        if (hasProfit) {
            return "이번 라운드는 전반적으로 좋은 흐름을 잡았고, "
                    + stockName(best) + " 거래가 특히 성과에 기여했어요.";
        }
        if (hasLoss) {
            return "이번 라운드는 변동성 대응이 쉽지 않았고, "
                    + stockName(worst) + " 거래에서 리스크 관리가 아쉬웠어요.";
        }
        return "이번 라운드는 분석할 거래가 부족해요. 다음에는 제안과 투표를 더 남겨보세요.";
    }

    private static String stockName(TradePnlSnapshot trade) {
        return trade != null && trade.stockName() != null && !trade.stockName().isBlank() ? trade.stockName() : "종목";
    }
}
