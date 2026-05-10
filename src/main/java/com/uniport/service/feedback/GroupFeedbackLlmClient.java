package com.uniport.service.feedback;

import java.util.Optional;

@FunctionalInterface
public interface GroupFeedbackLlmClient {

    Optional<String> generate(GroupFeedbackFacts facts);
}
