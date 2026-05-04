# Education Content API Design

## Goal
- Serve the handoff education package through Spring backend JSON APIs.
- Keep the current roadmap-style learning APIs intact.
- Add a parallel education content surface for overview -> cards -> quiz flow.

## Data Bundle
- Resource folder: `src/main/resources/education`
- Files:
  - `cards.json`
  - `education_overviews.json`
  - `education_quizzes.json`
  - `chart_svgs.js`

## Track Model
- Supported request tracks:
  - `intro_core`
  - `advanced_core`
  - `intro_sector`
  - `advanced_sector`
- Internal normalization:
  - card track `intro_sector_sector` maps to `intro_sector`
  - card track `advanced_sector_sector` maps to `advanced_sector`

## Endpoint Design

### 1. Catalog
- `GET /api/learning/education/catalog`
- Purpose:
  - list all tracks
  - expose available sectors
  - expose content version
- Output:
  - one row for `intro_core`
  - one row for `advanced_core`
  - one row per sector for `intro_sector`
  - one row per sector for `advanced_sector`

### 2. Day Content
- `GET /api/learning/education/days/{track}/{day}?sector=...`
- Purpose:
  - return one overview row
  - return all card rows for that day
  - return quiz availability summary
- Rules:
  - `sector` is required for sector tracks
  - core Day 20 and Day 26 can return empty card arrays and review quiz metadata
  - image cards include `svgPreset` when the handoff mapping exists

### 3. Quiz Content
- `GET /api/learning/education/quizzes/{track}/{day}?sector=...&mode=...`
- Purpose:
  - return normalized four-choice quiz questions
- Rules:
  - `mode` can be omitted
  - if omitted, backend infers:
    - core Day 20/26 -> `review`
    - sector tracks -> `sector`
    - other core days -> `daily`

### 4. Quiz Submission
- `POST /api/learning/education/quizzes/{track}/{day}/submit?sector=...&mode=...`
- Purpose:
  - persist selected quiz answers by handoff question id
  - return per-question correctness and day completion readiness
- Request:
  - `answers`
    - key: question id
    - value: selected option id (1-based)

### 5. Education Day Complete
- `POST /api/learning/education/days/{track}/{day}/complete?sector=...`
- Purpose:
  - complete one education day after all quiz questions have been answered
  - award point/exp style rewards through the shared learning user state
- Rules:
  - sector tracks still require `sector`
  - completion is blocked until every quiz question for that day has a saved answer
  - completion updates shared streak/point counters in `learning_user_states`

## Auth Policy
- Current implementation follows the existing `/api/learning` auth policy.
- These endpoints are mounted under the authenticated learning controller.
- If product later wants public content caching, controller-level auth can be relaxed separately.

## Response Shape Notes
- Overview and quiz fields closely follow the handoff spec.
- Card `visual` is returned as raw JSON so frontend can render by `imageType`.
- Card `svgPreset` is derived from `chart_svgs.js`.
- Question options are normalized into:
  - `id`
  - `text`

## Validation Expectations
- Expected counts from current handoff import:
  - cards: 608
  - overviews: 100
  - quizzes: 304
- Track distribution:
  - cards:
    - `intro_core`: 219
    - `advanced_core`: 229
    - `intro_sector`: 80 after normalization
    - `advanced_sector`: 80 after normalization
  - overviews:
    - `intro_core`: 30
    - `advanced_core`: 30
    - `intro_sector`: 20
    - `advanced_sector`: 20
  - quizzes:
    - `intro_core daily`: 72
    - `advanced_core daily`: 72
    - `intro_core review`: 20
    - `advanced_core review`: 20
    - `intro_sector sector`: 60
    - `advanced_sector sector`: 60

## Persistence Notes
- Education progress is stored in the existing `learning_user_states` table.
- Added JSON fields:
  - `educationCurrentDayJson`
  - `educationCompletedDaysJson`
  - `educationQuizAnswersJson`
- Existing roadmap learning progress fields remain intact.

## Next Step Recommendations
- Add tests for:
  - catalog counts
  - day filtering
  - sector normalization
  - svg preset mapping
  - quiz submission persistence
  - day completion guard conditions
