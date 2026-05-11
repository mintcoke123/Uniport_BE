# 2026-05-12 News FinBERT Session Handoff

## Repository Context

- Backend repository: `/Users/koyunseo/uniport_f/Uniport_BE`
- Frontend repository: `/Users/koyunseo/Desktop/workspace/uniport-clone`
- Backend branch at handoff: `main`
- Backend saved commit: `dd90f35 feat: add realtime news sentiment workflow`

## User Goal

뉴스 상세 원문 중심 구조는 투자 판단 가치가 낮아서, 실시간 투자 이슈 화면에서 다음을 실제 동작하게 만드는 것이 목표였다.

- 네이버 뉴스 등 실시간 뉴스 수집 결과를 투자 이슈로 보여준다.
- 뉴스 상세 화면 의존도를 낮추고 이슈 리스트에서 호재/악재를 바로 분류한다.
- 관련 종목 태그, 투자 포인트, 리스크 포인트를 함께 제공한다.
- 채팅방 공유 버튼으로 이슈를 공유한다.
- 감정 분류는 단순 목데이터가 아니라 실제 로직으로 동작해야 한다.
- 이후 FinBERT 계열 모델을 연결해서 분류 품질을 높일 수 있어야 한다.

## Backend Changes

### Realtime News API

주요 파일:

- `src/main/java/com/uniport/controller/RealtimeNewsController.java`
- `src/main/java/com/uniport/service/NewsService.java`
- `src/main/java/com/uniport/dto/RealtimeNewsListResponseDTO.java`
- `src/main/java/com/uniport/dto/RealtimeNewsItemDTO.java`
- `src/main/java/com/uniport/dto/RealtimeNewsDetailResponseDTO.java`
- `src/main/java/com/uniport/dto/RealtimeNewsRelatedStockDTO.java`
- `src/main/java/com/uniport/dto/RealtimeNewsSourceArticleDTO.java`
- `src/main/java/com/uniport/dto/NewsSharePreviewDTO.java`

현재 뉴스 응답에는 다음 필드가 포함된다.

- `sentiment`: `POSITIVE` 또는 `NEGATIVE`
- `sentimentLabel`: `호재` 또는 `악재`
- `sentimentScore`: 감정 신뢰도
- `sentimentReason`: 왜 그렇게 분류했는지
- `relatedStocks`: 관련 종목 태그
- `investmentPoints`: 투자 포인트
- `riskPoints`: 리스크 포인트

### FinBERT Sentiment Engine

추가된 백엔드 구성:

- `src/main/java/com/uniport/service/NewsSentimentAnalyzer.java`
- `src/main/java/com/uniport/service/DefaultNewsSentimentAnalyzer.java`
- `src/main/java/com/uniport/service/FinbertSentimentClient.java`
- `src/main/java/com/uniport/service/KeywordNewsSentimentAnalyzer.java`
- `src/main/java/com/uniport/service/NewsSentimentAnalysis.java`
- `src/main/java/com/uniport/service/NewsSentimentInput.java`
- `src/main/java/com/uniport/service/NewsSentimentType.java`

동작 방식:

1. `FINBERT_SENTIMENT_ENABLED=true`이면 백엔드가 외부 FinBERT 분석 서버에 뉴스 텍스트를 보낸다.
2. FinBERT 응답 confidence가 `FINBERT_SENTIMENT_MIN_CONFIDENCE` 이상이면 그 결과를 사용한다.
3. FinBERT 서버가 꺼져 있거나 confidence가 낮거나 neutral/unknown이면 `KeywordNewsSentimentAnalyzer`로 fallback한다.

설정:

```yaml
finbert:
  sentiment:
    enabled: ${FINBERT_SENTIMENT_ENABLED:false}
    base-url: ${FINBERT_SENTIMENT_BASE_URL:http://localhost:8011}
    min-confidence: ${FINBERT_SENTIMENT_MIN_CONFIDENCE:0.60}
```

### Local FinBERT Service

추가된 파일:

- `tools/finbert-sentiment-service/app.py`
- `tools/finbert-sentiment-service/requirements.txt`

실행 예시:

```bash
cd /Users/koyunseo/uniport_f/Uniport_BE/tools/finbert-sentiment-service
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 127.0.0.1 --port 8011
```

백엔드 실행 환경:

```bash
FINBERT_SENTIMENT_ENABLED=true
FINBERT_SENTIMENT_BASE_URL=http://127.0.0.1:8011
FINBERT_SENTIMENT_MIN_CONFIDENCE=0.60
```

주의:

- 현재 로컬 디스크 여유 공간이 매우 부족해서 Python dependency 설치와 모델 다운로드는 이 세션에서 완료하지 않았다.
- `df -h` 기준 root/Data 볼륨 여유 공간이 약 222MiB 수준이었다.
- 모델 설치 및 실행 전 수 GB 이상 여유 공간 확보가 필요하다.

## Frontend Changes

프론트 반영 위치:

- `feature/groupinvest/domain/src/commonMain/kotlin/com/crazyenough/uniport/feature/groupinvest/domain/model/GroupInvestNewsModels.kt`
- `feature/groupinvest/data/src/commonMain/kotlin/com/crazyenough/uniport/feature/groupinvest/data/dto/GroupInvestNewsDtos.kt`
- `feature/groupinvest/data/src/commonMain/kotlin/com/crazyenough/uniport/feature/groupinvest/data/mapper/GroupInvestNewsMapper.kt`
- `feature/groupinvest/presentation/src/commonMain/kotlin/com/crazyenough/uniport/feature/groupinvest/presentation/mocknews/GroupInvestMockNewsContract.kt`
- `feature/chat/presentation/src/commonMain/kotlin/com/crazyenough/uniport/feature/chat/presentation/room/ChatRoomContract.kt`
- `feature/chat/presentation/src/commonMain/kotlin/com/crazyenough/uniport/feature/chat/presentation/room/ChatRoomViewModel.kt`
- `feature/chat/presentation/src/commonMain/kotlin/com/crazyenough/uniport/feature/chat/presentation/room/ChatRoomScreen.kt`
- `composeApp/src/commonMain/kotlin/com/crazyenough/uniport/App.kt`

프론트 동작 방식:

- 백엔드가 내려주는 `sentiment`, `sentimentLabel`, `sentimentScore`, `sentimentReason`를 우선 사용한다.
- API 필드가 비어 있는 오래된 데이터에 대해서만 로컬 키워드 fallback이 동작한다.
- 이슈 공유 시 채팅 메시지에 관련 종목, 감정값, 감정 근거, 투자 포인트가 함께 전달된다.

## ETF And Trading Feedback Backlog

관련 문서:

- `docs/2026-05-12-finbert-feedback-improvement-backlog.md`

정리된 개선 방향:

- 나만의 ETF 구성 종목별 뉴스 흐름을 감정값으로 집계한다.
- ETF 리밸런싱 피드백에 종목 비중뿐 아니라 뉴스 흐름 근거를 포함한다.
- 매매 피드백에서 사용자의 매수/매도 시점과 당시 뉴스 감정을 비교한다.
- 단순 수익률 피드백을 “왜 그 판단이 위험했는지/괜찮았는지”까지 설명하는 구조로 개선한다.

## Verification

Backend verified:

```bash
./gradlew test --tests com.uniport.service.NewsServiceTest --tests com.uniport.controller.RealtimeNewsControllerTest --tests com.uniport.service.FinbertSentimentClientTest
```

Frontend verified:

```bash
./gradlew :feature:groupinvest:data:iosSimulatorArm64Test
./gradlew :feature:chat:presentation:compileKotlinIosSimulatorArm64 :feature:groupinvest:presentation:compileKotlinIosSimulatorArm64
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Frontend blocked verification:

- `:feature:groupinvest:presentation:iosSimulatorArm64Test`
- `:feature:chat:presentation:iosSimulatorArm64Test`

위 두 native test는 compile 이후 링크 단계에서 `No space left on device`로 중단됐다. 소스 컴파일 오류는 아니었고, 디스크 공간 확보 후 재실행해야 한다.

## Recommended Next Session Steps

1. 백엔드와 프론트의 최신 커밋 상태를 확인한다.
2. 로컬 디스크 공간을 확보한다.
3. FinBERT service dependency와 모델을 설치한다.
4. `FINBERT_SENTIMENT_ENABLED=true`로 백엔드를 실행한다.
5. 실시간 뉴스 API에서 `sentimentScore`, `sentimentReason`가 FinBERT 응답으로 채워지는지 확인한다.
6. 프론트 이슈 화면에서 호재/악재, 관련 종목, 투자 포인트, 채팅방 공유 payload가 실제 API 값으로 표시되는지 확인한다.
7. ETF/매매 피드백 개선은 `docs/2026-05-12-finbert-feedback-improvement-backlog.md` 순서대로 구현한다.
