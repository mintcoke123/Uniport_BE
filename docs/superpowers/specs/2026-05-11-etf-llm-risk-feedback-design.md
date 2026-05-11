# ETF LLM Risk Feedback Design

## Goal

ETF 분석 결과의 AI 리스크 진단을 실제 LLM 문장화로 제공한다. LLM은 백테스트와 편입 종목 데이터를 바탕으로 각 종목이 포트폴리오에 주는 영향을 설명한다. LLM 장애나 미설정 상황에서도 ETF 분석 자체는 실패시키지 않고, 서버의 룰 기반 피드백으로 대체한다.

## User Experience

분석 버튼을 누르면 백테스트 계산이 먼저 완료되고, 그 결과를 근거로 AI 피드백이 생성된다.

응답의 `aiFeedback.usedFallback`이 `false`이면 실제 LLM이 작성한 문장이다. `true`이면 LLM이 꺼져 있거나 실패해서 서버 fallback 문장을 사용한 것이다. 프론트는 이 값을 이용해 디버그나 관리자 화면에서 실제 LLM 사용 여부를 확인할 수 있다.

피드백은 다음 형태를 유지한다.

- `title`: "AI 리스크 진단"
- `summary`: 1-2문장 요약
- `bullets`: 최대 3개, 상위 편입 종목 중심
- `tone`: `BALANCED` 또는 `CAUTION`
- `disclaimer`: 과거 데이터 기반이며 미래 수익을 보장하지 않는다는 문구

## Architecture

기존 `EtfDataService`의 ETF 분석 흐름은 유지한다.

1. ETF 편입 종목과 비중을 읽는다.
2. Yahoo 기반 가격 데이터로 백테스트를 계산한다.
3. `InsightFacts`에 백테스트 지표와 편입 종목 정보를 함께 담는다.
4. `EtfAiFeedbackService.buildFeedback()`이 LLM 클라이언트를 호출한다.
5. LLM 응답이 유효하면 그대로 사용하고, 실패하거나 검증에 걸리면 fallback 피드백을 사용한다.

LLM 호출 구현은 `OpenAiFeedbackClient`가 담당한다. 설정은 기존 `openai.api-key`, `openai.base-url`, `openai.model`, `openai.feedback.enabled`를 사용한다.

## LLM Contract

LLM 입력은 `InsightFacts` JSON만 사용한다. 주요 입력은 다음과 같다.

- 포트폴리오명, 기간, 원금
- 총수익률, 예상 수익금, 변동성, 최대 낙폭
- 벤치마크 수익률과 초과 수익률
- 리스크 등급
- 상위 종목명, 상위 종목 비중, 상위 3개 비중
- 주요 섹터와 섹터 비중
- 편입 종목 목록: `securityId`, `name`, `weightPercent`, `sector`

LLM 출력은 JSON schema로 강제한다.

- `title`: string
- `summary`: string
- `tone`: `BALANCED` 또는 `CAUTION`
- `bullets`: 최대 3개
- 각 bullet: `type`은 `STRENGTH`, `RISK`, `INFO` 중 하나, `message`는 string

## Guardrails

LLM은 fact에 없는 숫자, 뉴스, 전망, 사건을 만들 수 없다. 서버는 응답 검증에서 다음 조건을 확인한다.

- 금지 표현: 무조건, 확실히, 보장, 매수, 매도, 추천 종목, 수익을 낼 수밖에
- summary 길이 120자 이하
- bullet 최대 3개
- 응답 안의 숫자가 입력 fact에 있는 숫자와 일치하는지 확인
- 유효하지 않으면 fallback 사용

## Fallback Behavior

LLM이 비활성화되었거나 API key가 없거나 호출 실패, 파싱 실패, 검증 실패가 발생하면 서버 룰 기반 피드백을 사용한다.

fallback도 상위 편입 종목 3개를 우선 설명한다. 비중이 40% 이상이면 단일 종목 영향 리스크, 25% 이상이면 핵심 비중 종목, 그 미만이면 분산 기여 종목으로 설명한다.

ETF 분석 API는 LLM 실패 때문에 실패하지 않는다. 대신 `aiFeedback.usedFallback=true`, metadata의 `usedFallbackMessage=true`, `llmModel`과 `promptVersion` 값으로 실제 LLM 사용 여부를 드러낸다.

## Observability

LLM 사용 여부를 응답 metadata에 노출한다.

- `metadata.usedFallbackMessage`
- `metadata.llmModel`
- `metadata.promptVersion`
- `aiFeedback.usedFallback`

추가 구현에서는 LLM 호출 실패 원인을 서버 로그에 남기되, 사용자 응답에는 내부 오류나 API key 관련 내용을 노출하지 않는다.

## Testing

단위 테스트는 다음을 검증한다.

- API key 또는 enabled 설정이 없으면 LLM 호출 없이 fallback 사용
- 정상 LLM JSON이면 `usedFallback=false`
- 금지 표현이 포함되면 fallback 사용
- fact에 없는 숫자가 포함되면 fallback 사용
- 상위 편입 종목이 있으면 종목별 bullet이 우선 생성
- ETF 분석 응답 metadata에 LLM 모델명, 프롬프트 버전, fallback 여부가 포함

통합 검증은 실제 OpenAI 호출 대신 mock `RestTemplate` 또는 fake `LlmFeedbackClient`를 사용한다. 실제 API key가 필요한 네트워크 테스트는 기본 테스트 스위트에 넣지 않는다.
