package com.uniport.service.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.service.openai.OpenAiChatCompletionClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAiGroupFeedbackClient implements GroupFeedbackLlmClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAiChatCompletionClient chatCompletionClient;

    public OpenAiGroupFeedbackClient(OpenAiChatCompletionClient chatCompletionClient) {
        this.chatCompletionClient = chatCompletionClient;
    }

    @Override
    public Optional<String> generate(GroupFeedbackFacts facts) {
        try {
            return chatCompletionClient.generateJson(
                    systemPrompt(),
                    OBJECT_MAPPER.writeValueAsString(facts),
                    responseFormat(),
                    outputText -> {
                        Map<String, Object> parsed = OBJECT_MAPPER.readValue(outputText, new TypeReference<>() {
                        });
                        Object comment = parsed.get("comment");
                        return comment != null ? String.valueOf(comment) : null;
                    }
            );
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String systemPrompt() {
        return """
                너는 Z세대 투자 입문자를 위한 투자 학습 서비스 UniPort의 매매 코치다.
                아래 JSON fact만 사용해 그룹 모의투자의 거래내역, 종목, 매매 이유에 대한 피드백을 작성한다.
                규칙:
                - 2문장 이내로 작성한다.
                - 220자 이내로 작성한다.
                - 거래내역, 종목명, 매수/매도 방향, 매매 이유, 수익/손실 중 최소 3가지를 반영한다.
                - 매매 이유가 결과적으로 타당했는지 또는 어떤 점이 부족했는지 판단한다.
                - 좋았던 매매 판단과 아쉬운 매매 판단을 최대한 구체적으로 짚는다.
                - 마지막에는 다음 매매 때 바로 적용할 기준을 1개 제안한다.
                - 단순 수익률 요약이 아니라 다음 매매 판단에 도움이 되는 피드백으로 쓴다.
                - 종목명은 fact에 있는 이름 그대로 사용한다.
                - fact에 없는 숫자, 사건, 뉴스, 전망을 만들지 않는다.
                - 향후 매수/매도 추천처럼 보이는 표현을 쓰지 않는다.
                - 특정 팀원을 비난하지 않는다.
                - 금지어: 무조건, 반드시, 추천, 확실히 오른다, 실패했다, 잘못했다, 책임
                출력은 JSON만 반환한다.
                """;
    }

    private Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "group_investment_feedback",
                        "strict", true,
                        "schema", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("comment"),
                                "properties", Map.of("comment", Map.of("type", "string"))
                        )
                )
        );
    }

}
