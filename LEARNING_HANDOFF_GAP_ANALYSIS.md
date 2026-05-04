# Learning Handoff Gap Analysis

## Source
- Handoff package: `uniport-education-backend-handoff`
- Key references:
  - `README_BACKEND_HANDOFF.md`
  - `education-content-api.md`
  - `KMP_HANDOFF.md`
  - `cards.json`
  - `education_overviews.json`
  - `education_quizzes.json`

## Current Backend State
- Existing learning API is progress-centric.
- Main endpoints are:
  - `GET /api/learning/home`
  - `GET /api/learning/courses`
  - `POST /api/learning/courses/{courseId}/start`
  - `GET /api/learning/courses/{courseId}`
  - `GET /api/learning/courses/{courseId}/days/{dayId}`
  - `POST /api/learning/steps/{stepId}/submit`
  - `POST /api/learning/courses/{courseId}/days/{dayId}/complete`
- Data source is DB-backed for course catalog and user state, but the seeded course content still comes from `LearningMockDataProvider`.
- Current content model is:
  - category-based course list
  - day roadmap
  - step-by-step lesson content
  - step answer submission and day completion

## Handoff Target State
- Handoff model is content-centric.
- Expected learning flow is:
  - overview page
  - card slide sequence
  - four-choice quiz
- Content package size:
  - cards: 608
  - overviews: 100
  - quizzes: 304
- Track families:
  - `intro_core`
  - `advanced_core`
  - `intro_sector`
  - `advanced_sector`
- Sector tracks cover 10 sectors and 2 days per sector.

## Main Gaps
- Current backend returns course/day/step DTOs.
  - Handoff expects catalog/day-content/quiz DTOs.
- Current backend user progress is tightly mixed with content retrieval.
  - Handoff separates static content endpoints from progress endpoints.
- Current backend content payload is small and authored for roadmap flow.
  - Handoff requires large normalized education payloads with overview/card/quiz structure.
- Current backend does not expose `svgPreset`.
  - Handoff prefers `svgPreset` for image cards.
- Current backend track model is `MAIN`, `MINI`, `ADVANCED`.
  - Handoff track model is `intro_core`, `advanced_core`, `intro_sector`, `advanced_sector`.

## Normalization Notes
- Handoff `cards.json` uses:
  - `intro_core`
  - `advanced_core`
  - `intro_sector_sector`
  - `advanced_sector_sector`
- Overviews and quizzes already use:
  - `intro_core`
  - `advanced_core`
  - `intro_sector`
  - `advanced_sector`
- Backend normalization rule added:
  - `intro_sector_sector` -> `intro_sector`
  - `advanced_sector_sector` -> `advanced_sector`

## What Was Added
- New content endpoints:
  - `GET /api/learning/education/catalog`
  - `GET /api/learning/education/days/{track}/{day}?sector=...`
  - `GET /api/learning/education/quizzes/{track}/{day}?sector=...&mode=...`
- New progress endpoints:
  - `POST /api/learning/education/quizzes/{track}/{day}/submit?sector=...&mode=...`
  - `POST /api/learning/education/days/{track}/{day}/complete?sector=...`
- New backend resource bundle:
  - `src/main/resources/education/cards.json`
  - `src/main/resources/education/education_overviews.json`
  - `src/main/resources/education/education_quizzes.json`
  - `src/main/resources/education/chart_svgs.js`
- New service:
  - `EducationContentService`
- Shared learning state persistence:
  - `learning_user_states.educationCurrentDayJson`
  - `learning_user_states.educationCompletedDaysJson`
  - `learning_user_states.educationQuizAnswersJson`

## Remaining Gaps After This Change
- Existing roadmap-based learning APIs and handoff content APIs coexist, but are not unified into one progress model yet.
- SVG asset files are still frontend-side concerns; backend only returns `svgPreset`.
- There are no dedicated automated tests yet for the new education endpoints.

## Recommendation
- Keep existing `/api/learning/*` roadmap APIs for current temp frontend flow.
- Use `/api/learning/education/*` as the content API for the handoff-based education experience.
- If the product converges on the handoff flow, next steps should be:
  - wire temp frontend quiz screens to the new submit/complete endpoints
  - add endpoint-level integration tests for day and sector tracks
  - decide whether roadmap and handoff progress should be merged in one UX or kept parallel
