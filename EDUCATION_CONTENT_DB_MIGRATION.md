# Education Content DB Migration

## Summary

Education content is no longer served directly from resource JSON at runtime.

- Runtime source of truth:
  - `education_overviews`
  - `education_cards`
  - `education_quizzes`
- Seed source:
  - `src/main/resources/education/education_overviews.json`
  - `src/main/resources/education/cards.json`
  - `src/main/resources/education/education_quizzes.json`
  - `src/main/resources/education/chart_svgs.js`

## What Changed

`EducationContentService` now:

1. checks whether the education tables are empty at startup
2. seeds the tables from the existing handoff JSON when needed
3. serves catalog/day/quiz APIs from JPA repositories

This means the app no longer depends on in-memory resource parsing for normal API responses.

## New Tables

### `education_overviews`

Stores day-level overview content.

Main fields:

- `track`
- `sector`
- `day_number`
- `level_label`
- `day_label`
- `title`
- `summary1`
- `summary2`
- `key_points_json`
- `cta_label`

### `education_cards`

Stores card-by-card learning content.

Main fields:

- `source_idx`
- `asset_id`
- `sheet`
- `track`
- `sector`
- `day_number`
- `section`
- `card_number`
- `title`
- `text`
- `image_type`
- `svg_preset`
- `visual_json`

### `education_quizzes`

Stores question-level quiz content.

Main fields:

- `source_mode`
- `track`
- `sector`
- `day_number`
- `quiz_number`
- `quiz_type`
- `question`
- `options_json`
- `answer_index`
- `topic`
- `area`
- `intent`

## Runtime Flow

### Catalog

`GET /api/learning/education/catalog`

- reads from `education_overviews`
- groups by `track + sector`
- derives total day count per track

### Day Content

`GET /api/learning/education/days/{track}/{day}`

- overview from `education_overviews`
- cards from `education_cards`
- quiz availability from `education_quizzes`

### Quiz

`GET /api/learning/education/quizzes/{track}/{day}`

- reads ordered questions from `education_quizzes`

### Quiz Submit / Day Complete

- user progress is still stored in `learning_user_states`
- content body comes from the new education tables

## Current Management Model

At this stage, education content is:

- editable in the database directly
- initially populated from bundled JSON resources
- not yet exposed through the admin console

So the bundled JSON is now a bootstrap seed, not the live runtime content source.
