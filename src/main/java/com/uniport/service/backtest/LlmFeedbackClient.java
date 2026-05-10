package com.uniport.service.backtest;

import java.util.Optional;

public interface LlmFeedbackClient {

    Optional<RuleBasedFeedback> generate(InsightFacts facts);

    String modelName();

    String promptVersion();
}
