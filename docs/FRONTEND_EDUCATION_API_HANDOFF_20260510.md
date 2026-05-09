# UniPort Education Frontend API Handoff

작성일: 2026-05-10  
대상: KMP Compose 프론트엔드  
브랜치: `education`  
운영 API Base URL: `https://uniportbe-production.up.railway.app`

---

## 1. 결론

프론트는 이제 `cards.json`을 앱에 직접 넣지 말고, Railway 백엔드 API 응답을 받아 Figma 학습 화면에 렌더링하면 됩니다.

백엔드에는 다음이 반영되어 있습니다.

- `cards.json` 콘텐츠가 운영 DB `education_cards`에 시드됨
- `education_overviews`, `education_cards`, `education_quizzes` 운영 DB 반영 완료
- 기존 mock `learning_courses` 테이블 삭제 완료
- 기존 `/api/learning/*` mock API 제거 완료
- 새 `/api/v1/education/*` API로 학습 화면/진도/퀴즈/완료 처리 통합
- 온보딩에서 선택한 섹터 2개와 난이도 기반으로 로드맵 구성

운영 DB 확인값:

```text
education_overviews: 100
education_cards: 608
education_quizzes: 304
learning_courses: deleted
```

---

## 2. 인증

대부분의 `/api/v1/education/*` API는 사용자별 진도와 섹터 선택을 다루기 때문에 Firebase Bearer token이 필요합니다.

요청 헤더:

```http
Authorization: Bearer {firebase_id_token}
Content-Type: application/json
```

토큰 없이 호출하면 아래 응답이 나오는 것이 정상입니다.

```json
{
  "success": false,
  "message": "Authorization Bearer token is required",
  "errorCode": "AUTH_TOKEN_REQUIRED",
  "requestId": null
}
```

---

## 3. 화면별 API 매핑

### 3-1. 교육 홈

Figma 역할:
- 상단 사용자 요약
- 메인 코스 / 미니 코스 탭

API:

```http
GET /api/v1/education/home
```

주요 응답 필드:

```json
{
  "content_version": "2026-05-09.1",
  "user": {
    "level_label": "Lv.0",
    "point": 3000,
    "profile_asset_key": "profile_animal_default"
  },
  "tabs": [
    { "key": "main", "label": "메인 코스", "selected": true },
    { "key": "mini", "label": "미니 코스", "selected": false }
  ]
}
```

---

### 3-2. 교육 / 메인 코스 선택

Figma 화면:
- 교육/메인 코스
- 입문 30일 코스
- 초급 30일 코스
- 중급 30일 코스

API:

```http
GET /api/v1/education/courses?tab=main
```

프론트 처리:

- `courses[]`를 코스 카드로 렌더링
- `status_label`은 카드 상태 문구
- `progress_label`은 `Day 02 / 30` 같은 진행 문구
- `primary_action.enabled=false`면 버튼 비활성화
- `cta_type` 또는 `primary_action.type`으로 버튼 동작 분기

주요 응답 필드:

```json
{
  "course_id": "advanced",
  "title": "초급 30일 코스",
  "subtitle": "실전 감각을 익히는 심화 과정",
  "total_days": 30,
  "current_day": 2,
  "completed_days": 1,
  "progress_percent": 3,
  "progress_label": "Day 02 / 30",
  "status": "in_progress",
  "status_label": "현재 이수중",
  "cover_asset_key": "course_cover_advanced_main",
  "is_locked": false,
  "locked_reason": null,
  "cta_type": "continue",
  "action_label": "이어하기",
  "primary_action": {
    "type": "continue",
    "label": "이어하기",
    "enabled": true,
    "target_id": "advanced"
  }
}
```

코스 `status` enum:

```text
unlocked
in_progress
completed
locked
```

---

### 3-3. 30일 코스 로드맵

Figma 화면:
- 메인코스/입문 30일
- 메인코스/초급 30일
- 메인코스/중급 30일

API:

```http
GET /api/v1/education/courses/{courseId}/roadmap
```

예:

```http
GET /api/v1/education/courses/intro/roadmap
GET /api/v1/education/courses/advanced/roadmap
GET /api/v1/education/courses/intermediate/roadmap
```

30일 구조:

```text
Day 1~26: 코어 콘텐츠
Day 27~28: 사용자가 선택한 섹터 A
Day 29~30: 사용자가 선택한 섹터 B
```

프론트 처리:

- `days[]`를 로드맵 노드로 렌더링
- `status=current`는 현재 학습 노드
- `status=locked`는 잠김 노드
- `status=completed`는 완료 노드
- `primary_action.enabled=false`면 진입 막기
- 잠긴 Day를 직접 호출하면 서버가 `DAY_LOCKED`로 막음

주요 응답 필드:

```json
{
  "content_version": "2026-05-09.1",
  "course": {
    "course_id": "intro",
    "title": "입문 30일 코스",
    "subtitle": "투자의 기초를 탄탄하게 다지는 첫걸음",
    "total_days": 30,
    "core_days": 26,
    "sector_days": 4
  },
  "user_progress": {
    "level_label": "Lv.0",
    "point": 3000,
    "current_day": 1,
    "completed_days": 0
  },
  "selected_sectors": [
    { "sector_id": "ai_semiconductor", "name": "AI 반도체", "order": 1 },
    { "sector_id": "quantum_computer", "name": "양자컴퓨터", "order": 2 }
  ],
  "days": [
    {
      "day": 1,
      "title": "왜 투자해야 하는가",
      "module_type": "core",
      "status": "current",
      "status_label": "현재 학습",
      "is_locked": false,
      "locked_reason": null,
      "progress_label": "Day 01 / 30",
      "cta_type": "continue",
      "action_label": "이어하기",
      "primary_action": {
        "type": "continue",
        "label": "이어하기",
        "enabled": true,
        "target_id": "intro_d1"
      },
      "card_count": 14,
      "quiz_count": 3
    }
  ]
}
```

Day `status` enum:

```text
current
available
locked
completed
```

---

## 4. 온보딩 섹터 선택 / 난이도

요구사항:

- 사용자는 온보딩에서 원하는 섹터 2개를 선택
- 난이도에 따라 `intro` 또는 `advanced` 코스가 시작됨
- 선택된 섹터 2개가 Day 27~30에 반영됨

백엔드 저장 위치:

```text
learning_user_states.education_sector_selections_json
learning_user_states.education_current_day_json
```

중요:

`learning_user_states`는 이름에 learning이 들어가 있지만 삭제하면 안 됩니다. 새 교육 API의 사용자 상태 저장 테이블입니다.

섹터 후보:

```text
battery: 2차전지
power_equipment: 전력기기
bio: 바이오
nuclear: 원전
space_rocket: 우주/로켓
ai_semiconductor: AI 반도체
defense: 방산
quantum_computer: 양자컴퓨터
autonomous_driving: 자율주행
robot: 로봇
```

섹터 선택 조회:

```http
GET /api/v1/education/courses/{courseId}/sector-selection
```

섹터 선택 변경:

```http
PUT /api/v1/education/courses/{courseId}/sector-selection
```

요청:

```json
{
  "selected_sector_ids": ["ai_semiconductor", "quantum_computer"]
}
```

주의:

- 반드시 2개를 보내야 함
- 중복 선택 불가
- Day 27 이상으로 진행했거나 섹터 Day를 완료한 뒤에는 변경 불가
- 변경 불가 시 `SECTOR_SELECTION_LOCKED`

---

## 5. 학습 Day 상세

Figma 템플릿:
- 학습/카드/개요
- 학습/카드/내용/text
- 학습/카드/내용/image
- 학습/사지선다 퀴즈/default

API:

```http
GET /api/v1/education/courses/{courseId}/days/{day}
```

예:

```http
GET /api/v1/education/courses/intro/days/1
```

프론트 처리:

- `flow[]`를 순서대로 렌더링
- 각 item의 `template_type`으로 Figma 템플릿 선택
- `primary_action`으로 하단 버튼 상태 처리
- `progress`로 상단 progress bar 렌더링
- `flow[].visual`이 있으면 이미지/차트/컴포넌트 영역 렌더링

상위 응답 주요 필드:

```json
{
  "content_version": "2026-05-09.1",
  "course_id": "intro",
  "course_label": "입문",
  "day": 1,
  "day_label": "입문 Day 1",
  "module_type": "core",
  "status": "current",
  "status_label": "현재 학습",
  "is_locked": false,
  "locked_reason": null,
  "title": "왜 투자해야 하는가",
  "estimated_minutes": 5,
  "progress": {
    "current_step": 1,
    "total_steps": 18,
    "completed_steps": 0,
    "progress_label": "1 / 18",
    "progress_ratio": 0.0555555556,
    "is_completed": false
  },
  "primary_action": {
    "type": "continue",
    "label": "이어하기",
    "enabled": true,
    "target_id": "intro_d1"
  },
  "flow": [],
  "completion_preview": {
    "template_type": "day_completion",
    "reward_exp": 500,
    "reward_point": 500,
    "streak_days": 0,
    "character_asset_key": "learning_complete_character_default"
  }
}
```

---

## 6. Figma 템플릿 매핑

`flow[].template_type`으로 Compose 화면을 선택하면 됩니다.

| API `template_type` | Figma 템플릿 | 프론트 렌더러 예시 |
|---|---|---|
| `day_overview` | 학습/카드/개요 | `DayOverviewScreen` |
| `content_text` | 학습/카드/내용/text | `TextLearningCard` |
| `content_visual` | 학습/카드/내용/image | `VisualLearningCard` |
| `quiz_single_choice` + `not_selected` | 학습/사지선다 퀴즈/default | `QuizScreen` |
| `quiz_single_choice` + client selected | 학습/사지선다 퀴즈/hover | 프론트 로컬 상태 |
| `quiz_single_choice` + `submitted_correct` | 학습/사지선다 퀴즈/정답 | `QuizResultScreen` |
| `quiz_single_choice` + `submitted_wrong` | 학습/사지선다 퀴즈/오답 | `QuizResultScreen` |
| `day_completion` | 학습 완료 | `DayCompletionScreen` |

---

## 7. Flow Item 구조

### 7-1. 개요 화면

```json
{
  "step_id": "intro_d1_overview",
  "step_type": "overview",
  "template_type": "day_overview",
  "step_order": 1,
  "total_steps": 18,
  "title": "왜 투자해야 하는가",
  "body": [
    "월급만으로는 늘 불안한 이유를 숫자로 마주하는 날이에요.",
    "돈이 일하게 만드는 감각이 처음으로 생기기 시작합니다."
  ],
  "key_concepts": [
    "인플레이션이 왜 내 돈의 적인지 이해한다"
  ],
  "visual": {
    "visual_type": "component",
    "visual_key": "intro_day1_overview",
    "asset_key": "",
    "alt": "왜 투자해야 하는가"
  },
  "progress": {
    "current_step": 1,
    "total_steps": 18,
    "completed_steps": 0,
    "progress_label": "1 / 18",
    "progress_ratio": 0.0555555556,
    "is_completed": false
  },
  "primary_action": {
    "type": "continue",
    "label": "계속",
    "enabled": true,
    "next_step_id": "intro_d1_card_0"
  }
}
```

### 7-2. 텍스트 카드

```json
{
  "step_id": "intro_d1_card_1",
  "step_type": "card",
  "template_type": "content_text",
  "idx": 1,
  "sheet": "입문_카드_FINAL",
  "track": "intro_core",
  "sector": null,
  "day": 1,
  "section": "① 인플레이션과 내 돈의 가치 하락",
  "card_number": "2/2",
  "asset_id": "intro-core-d1-...",
  "title": "핵심 포인트",
  "text": "• 인플레이션 앞에서 현금만 들고 있는 것은 사실상 구매력이 줄어드는 흐름을 방치하는 것과 같아요.",
  "image_type": "placeholder",
  "visual_type": "none",
  "visual_key": "intro-core-d1-...",
  "asset_key": null
}
```

### 7-3. 이미지 / 비주얼 카드

```json
{
  "step_id": "intro_d1_card_0",
  "step_type": "card",
  "template_type": "content_visual",
  "idx": 0,
  "card_number": "1/2",
  "title": "인플레이션과 내 돈의 가치 하락",
  "text": "• 물가가 오르면 같은 1만 원으로 살 수 있는 양이 줄어요.",
  "image_type": "image",
  "visual_type": "raster_asset",
  "visual_key": "intro-core-d1-...",
  "asset_key": "intro-core-d1-...",
  "card_visual": {},
  "visual": {
    "visual_type": "raster_asset",
    "visual_key": "intro-core-d1-...",
    "asset_key": "intro-core-d1-...",
    "alt": "인플레이션과 내 돈의 가치 하락",
    "payload": {},
    "render_policy": {
      "fit": "contain",
      "allow_crop": false
    }
  }
}
```

`visual_type` enum:

```text
none
component
raster_asset
chart_asset
statement_component
trading_screen_component
company_example_component
sector_supply_chain_component
character_raster
```

KMP 처리 원칙:

- `content_text`: visual 영역 없이 텍스트 중심 카드
- `content_visual`: visual 영역 렌더링
- `visual_type=raster_asset`: `asset_key`로 앱 asset/CDN 매핑
- `visual_type=statement_component`: `visual.payload`로 표/재무제표형 UI 구성
- `visual_type=component`: `visual_key` 기준으로 Compose renderer 선택
- `render_policy.allow_crop=false`면 이미지 crop 금지

---

## 8. 텍스트 렌더링 규칙

백엔드는 카드 원문 `text`를 수정하지 않고 그대로 내려줍니다.

프론트는 Markdown-lite 방식으로 파싱해서 Figma 텍스트 계층에 맞춰 렌더링하세요.

지원해야 할 최소 규칙:

```text
• bullet line
**bold**
실전 체크리스트:
주의 포인트:
```

추천 모델:

```kotlin
data class RichTextBlock(
    val type: RichTextBlockType,
    val spans: List<RichTextSpan>
)

enum class RichTextBlockType {
    PARAGRAPH,
    BULLET,
    CHECKLIST,
    SUMMARY
}

data class RichTextSpan(
    val text: String,
    val bold: Boolean = false
)
```

---

## 9. 퀴즈

### 9-1. 퀴즈 조회

API:

```http
GET /api/v1/education/quiz/{quizId}
```

예:

```http
GET /api/v1/education/quiz/intro_d1_q1
```

응답 특징:

- `template_type=quiz_single_choice`
- `quiz_state=not_selected`
- 정답은 내려주지 않음

```json
{
  "content_version": "2026-05-09.1",
  "quiz_id": "intro_d1_q1",
  "template_type": "quiz_single_choice",
  "question": "질문",
  "choices": [
    { "choice_id": "a", "text": "선택지 A" },
    { "choice_id": "b", "text": "선택지 B" }
  ],
  "quiz_state": "not_selected"
}
```

프론트 처리:

- 사용자가 선택지를 누른 상태는 프론트 로컬 상태로 처리
- 선택 전에는 제출 버튼 비활성화
- 선택 후에는 제출 버튼 활성화
- 정답 표시는 제출 API 응답 이후에만 처리

### 9-2. 퀴즈 제출

API:

```http
POST /api/v1/education/quiz-attempts
```

요청:

```json
{
  "quiz_id": "intro_d1_q1",
  "selected_choice_id": "a",
  "course_id": "intro",
  "day": 1
}
```

정답 응답:

```json
{
  "content_version": "2026-05-09.1",
  "attempt_id": "attempt_intro_d1_q1_a",
  "quiz_id": "intro_d1_q1",
  "selected_choice_id": "a",
  "correct_choice_id": "a",
  "is_correct": true,
  "quiz_state": "submitted_correct",
  "feedback_title": "정답이에요!",
  "explanation": "해설",
  "next_action": {
    "type": "continue",
    "next_step_id": "intro_d1_completion"
  }
}
```

오답 응답:

```json
{
  "quiz_state": "submitted_wrong",
  "is_correct": false,
  "feedback_title": "오답이에요!",
  "selected_choice_id": "b",
  "correct_choice_id": "a",
  "explanation": "해설"
}
```

---

## 10. 카드 완료 / Day 완료

### 10-1. 카드 완료

API:

```http
POST /api/v1/education/progress/cards/complete
```

요청:

```json
{
  "course_id": "intro",
  "day": 1,
  "idx": 0,
  "client_completed_at": "2026-05-10T01:00:00+09:00"
}
```

응답:

```json
{
  "content_version": "2026-05-09.1",
  "progress": {
    "course_id": "intro",
    "day": 1,
    "completed_cards": 1,
    "total_cards": 14,
    "current_card_index": 1,
    "progress_label": "1 / 14",
    "progress_ratio": 0.0714285714,
    "can_complete_day": false,
    "is_day_completed": false
  }
}
```

특징:

- 중복 호출 안전
- 같은 카드를 여러 번 완료해도 중복 카운트되지 않음
- 잠긴 Day의 카드 완료는 `DAY_LOCKED`

### 10-2. Day 완료

API:

```http
POST /api/v1/education/courses/{courseId}/days/{day}/complete
```

요청:

```json
{
  "last_step_id": "intro_d1_q3",
  "client_completed_at": "2026-05-10T01:10:00+09:00"
}
```

응답:

```json
{
  "content_version": "2026-05-09.1",
  "template_type": "day_completion",
  "course_id": "intro",
  "day": 1,
  "completion_title": "오늘도 정복 완료!",
  "completion_subtitle": "고생 많으셨어요",
  "streak": {
    "days": 1,
    "label": "1일 연속!"
  },
  "reward": {
    "exp": 500,
    "point": 500,
    "total_exp": 500,
    "total_point": 500
  },
  "character_asset_key": "learning_complete_character_default",
  "next_action": {
    "type": "roadmap",
    "label": "로드맵으로 돌아가기",
    "next_day": 2,
    "course_completed": false
  }
}
```

특징:

- 멱등 처리됨
- 같은 Day 완료를 여러 번 호출해도 보상 중복 지급 없음
- 잠긴 Day 완료는 `DAY_LOCKED`

---

## 11. 에러 처리

프론트에서 분기해야 하는 주요 에러:

| error/message | 상황 | 프론트 처리 |
|---|---|---|
| `AUTH_TOKEN_REQUIRED` | 토큰 없음 | 로그인/토큰 갱신 |
| `DAY_LOCKED` | 잠긴 Day 직접 진입 | 로드맵으로 돌려보내고 잠김 안내 |
| `SECTOR_SELECTION_REQUIRED` | Day 27 이후인데 섹터 2개 없음 | 섹터 선택 화면으로 이동 |
| `SECTOR_SELECTION_INVALID_COUNT` | 섹터 2개가 아님 | 선택 개수 안내 |
| `SECTOR_SELECTION_LOCKED` | 섹터 변경 불가 상태 | 변경 불가 안내 |
| `QUIZ_NOT_FOUND` | 퀴즈 ID 오류 | 콘텐츠 오류 fallback |
| `CARD_NOT_FOUND` | 카드 idx 오류 | 콘텐츠 오류 fallback |

---

## 12. 프론트 작업 순서

1. API client에 Base URL 설정
2. Firebase ID token을 `Authorization` 헤더에 붙이기
3. `GET /api/v1/education/courses?tab=main`으로 코스 선택 화면 연결
4. 코스 클릭 시 `GET /api/v1/education/courses/{courseId}/roadmap`
5. 현재 Day 클릭 시 `GET /api/v1/education/courses/{courseId}/days/{day}`
6. `flow[].template_type`으로 Figma 템플릿 라우팅
7. 카드 넘김 시 `POST /api/v1/education/progress/cards/complete`
8. 퀴즈 선택 후 `POST /api/v1/education/quiz-attempts`
9. 마지막 step 이후 `POST /api/v1/education/courses/{courseId}/days/{day}/complete`
10. 완료 화면에서 `next_action`에 따라 로드맵으로 이동

---

## 13. QA 체크리스트

프론트 QA는 아래를 확인하면 됩니다.

- 코스 선택 화면에서 3개 코스가 보이는가
- 코스 카드의 상태 문구와 버튼 문구가 API 값과 일치하는가
- 로드맵에서 현재 Day와 잠긴 Day가 구분되는가
- 잠긴 Day를 직접 열려고 하면 막히는가
- Day 1 상세에서 개요, 텍스트 카드, 이미지 카드, 퀴즈가 순서대로 나오는가
- `content_text`는 텍스트 카드 템플릿으로 나오는가
- `content_visual`은 이미지 카드 템플릿으로 나오는가
- 퀴즈 조회 시 정답이 노출되지 않는가
- 퀴즈 제출 후 정답/오답 bottom sheet가 API 응답대로 나오는가
- Day 완료 시 보상/연속 학습/캐릭터/로드맵 버튼이 나오는가
- Day 완료를 두 번 호출해도 포인트가 중복 지급되지 않는가
- 온보딩에서 선택한 섹터 2개만 Day 27~30에 나오는가

---

## 14. 백엔드 현재 상태

현재 운영 배포:

```text
Railway service: Uniport_BE
latest deployment: SUCCESS
branch: education
```

현재 코드 커밋:

```text
d31707b feat: enrich education ui state contract
ea8c3eb refactor: remove legacy learning course api
4a95c15 fix: align education content version
08362ae chore: add education content recreate script
1cb1c42 feat: add kmp education api
```

검증 명령:

```bash
bash gradlew test --rerun-tasks
```

검증 결과:

```text
BUILD SUCCESSFUL
```
