# Destiny Oracle — Backend

Spring Boot 3.3 · Java 21 (GraalVM) · PostgreSQL · Spring AI (Claude) · Gemini Imagen 3

---

## Core Concept

Each user has up to 10 **Destiny Cards** — one per life aspect (Health, Career, Finances…).
Every card tracks a personal journey through **6 emotional stages**:

```
Storm → Fog → Clearing → Aura → Radiance → Legend
```

The user enters their **fear** ("I'm afraid I'll never feel healthy") and their **dream**
("I want to run a marathon by 40"). Claude turns those raw words into a full personal narrative.

---

## AI Pipeline — Core Logic (Read This First)

This is the most important part of the system.
Three AI calls happen in sequence for every card. **Order is mandatory.**

```
User submits fear + dream text
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│  STEP 1 — Stage Content Generation  (Claude)                    │
│                                                                 │
│  File:    service/impl/StageContentGenerationServiceImpl.java   │
│  Trigger: AUTO on POST /api/v1/cards (card creation)           │
│  Manual:  POST /api/v1/cards/{cardId}/generate-stage-content   │
│  Re-gen:  POST /api/v1/cards/{cardId}/regenerate-stage-content  │
│                                                                 │
│  Input:  aspectLabel + fearText + dreamText                     │
│  Claude writes 6 × {title, tagline, lore} — one per stage      │
│  Saved:  card_stage_content (title, tagline, lore columns)      │
│                                                                 │
│  Example output for "Health & Body":                            │
│    storm    → title: "The Body's Betrayal"                      │
│               tagline: "Years of ignoring what matters most"    │
│               lore: "You've known for a while. The warning      │
│                      signs were there — the shortness of        │
│                      breath, the exhaustion after one flight    │
│                      of stairs. This is the moment of truth."   │
│    fog      → title: "The Uncertain Step" / ...                 │
│    clearing → title: "First Light Breaking" / ...               │
│    aura     → title: "The Living Proof" / ...                   │
│    radiance → title: "Marathon Body" / ...                      │
│    legend   → title: "The Unbreakable" / ...                    │
│                                                                 │
│  WHY THIS IS FIRST:                                             │
│  Step 2 reads these titles + lore as emotional context.         │
│  Without them, image prompts are generic — not personal.        │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼  stage content saved to DB
          │
┌─────────────────────────────────────────────────────────────────┐
│  STEP 2 — Image Prompt Generation  (Claude)                     │
│                                                                 │
│  File:    service/impl/ImagePromptServiceImpl.java              │
│  Trigger: AUTO inside generate-images pipeline                  │
│  Manual:  POST /api/v1/cards/{cardId}/generate-prompts          │
│                                                                 │
│  Input:  stage content (title + lore from Step 1)              │
│  Claude writes 6 Imagen-ready text prompts — one per stage      │
│  Saved:  card_stage_content.image_prompt column                 │
│                                                                 │
│  Example for storm stage:                                       │
│    "chibi anime character, hunched figure in heavy rain,        │
│     dark storm clouds, oppressive shadows, cinematic gloom,     │
│     tarot card composition, ornate gold border, 4k"             │
│                                                                 │
│  Cost saving: if promptStatus = READY on the card,              │
│  Claude is skipped and existing prompts are reused from DB.     │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼  image prompts saved to DB
          │
┌─────────────────────────────────────────────────────────────────┐
│  STEP 3 — Card Image Generation  (Gemini Imagen 3)              │
│                                                                 │
│  File:    service/impl/CardImageGenerationServiceImpl.java      │
│  Trigger: POST /api/v1/cards/{cardId}/generate-images           │
│           POST /api/v1/cards/generate-images/all                │
│                                                                 │
│  Input:  image prompts from Step 2 + user's chibi avatar URL    │
│  Gemini generates 6 PNG images in PARALLEL (virtual threads)    │
│  Saved:  GCS bucket → card_images table → destiny_cards.        │
│          image_url updated to current stage image               │
│                                                                 │
│  Chibi reference: if user has a chibi avatar, it's sent as      │
│  REFERENCE_TYPE_STYLE so all 6 images share the same character. │
│  Typical time: 30–60 seconds for all 6 images.                  │
│                                                                 │
│  ★ GATE: Step 3 never starts until ALL 6 prompt steps           │
│    (Step 2) are DONE or SKIPPED.                                │
└─────────────────────────────────────────────────────────────────┘
```

### Key rules

| Rule | Why |
|------|-----|
| Step 1 before Step 2 | Image prompts use stage titles + lore as emotional context |
| Step 2 before Step 3 | Gemini needs the text prompt to generate an image |
| Steps are idempotent | If prompts already exist in DB, Claude is skipped (saves cost) |
| `promptStatus` tracks Step 2 | `NONE → GENERATING → READY \| FAILED` on `destiny_cards` |
| Regenerate resets status | `regenerate-stage-content` resets `promptStatus = NONE` so images also regenerate |

### When does Step 1 run?

- **Automatically** when the user adds a new card (`POST /api/v1/cards`)
- **Manually** if it failed at creation time (`POST /{cardId}/generate-stage-content`)
- **On re-prompt** when user edits their fear/dream (`POST /{cardId}/regenerate-stage-content`)

---

## Job Tracking (real-time pipeline visibility)

Every image generation run creates a `GenerationJob` with **12 steps** pre-built and
visible immediately so the UI can render the full pipeline before anything starts.

```
Steps 0–5   PROMPT phase  (Claude — sequential inside one batch call)
Steps 6–11  IMAGE  phase  (Gemini — all 6 fire in parallel)

Each step: WAITING → RUNNING → DONE | SKIPPED | FAILED
```

**Poll from the frontend every 2–3 seconds:**
```
GET /api/v1/cards/{cardId}/jobs/latest
```

Stop polling when `job.status` is `COMPLETED` or `FAILED`.

**Watch in real time (terminal):**
```bash
tail -f logs/pipeline.log
```

---

## Key Files

| File | Role |
|------|------|
| `StageContentGenerationServiceImpl.java` | **Step 1** — Claude writes title/tagline/lore for 6 stages from user's fear + dream |
| `ImagePromptServiceImpl.java` | **Step 2** — Claude writes 6 Gemini image prompts using stage content as context |
| `CardImageGenerationServiceImpl.java` | **Step 3** — Gemini Imagen generates 6 images in parallel; creates + updates GenerationJob |
| `JobStepUpdater.java` | Saves step/job state in `REQUIRES_NEW` transactions (UI sees updates instantly) |
| `CardServiceImpl.java` | `addCard()` auto-triggers Step 1 after card is created |
| `DataInitializer.java` | Seeds 5 starter cards with pre-written content for dev/demo |

---

## API Endpoints

### Stage Content (Step 1) — Core

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/cards` | Add card → **auto-triggers Step 1** |
| POST | `/api/v1/cards/{cardId}/generate-stage-content` | Manually trigger Step 1 |
| POST | `/api/v1/cards/{cardId}/regenerate-stage-content` | Re-run Step 1 after fear/dream edit |

### Image Pipeline (Steps 2 + 3)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/cards/{cardId}/generate-prompts` | Step 2 only — generate image prompts |
| POST | `/api/v1/cards/{cardId}/generate-images` | Steps 2+3 — full pipeline, all 6 stages |
| POST | `/api/v1/cards/{cardId}/generate-images/{stage}` | Regenerate one stage image |
| POST | `/api/v1/cards/generate-images/all` | Steps 2+3 for ALL user's aspect cards |

### Job Tracking (polling)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/cards/{cardId}/jobs/latest` | Latest job + all 12 step statuses |
| GET | `/api/v1/cards/{cardId}/jobs/{jobId}` | Specific job |
| GET | `/api/v1/cards/{cardId}/jobs` | Job history |

### Cards (CRUD)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/cards` | All cards (spread view) |
| GET | `/api/v1/cards/{cardId}` | Full card detail with stage content |
| GET | `/api/v1/cards/aspects` | Available aspects |
| PATCH | `/api/v1/cards/{cardId}` | Update fear/dream text |
| DELETE | `/api/v1/cards/{cardId}` | Remove card |
| PUT | `/api/v1/cards/{cardId}/habits/{habitId}/complete` | Toggle habit done |

---

## Environment Variables

```bash
# Required for Step 1 + Step 2 (Claude)
# Get key: https://console.anthropic.com/settings/keys
ANTHROPIC_API_KEY=sk-ant-...

# Required for Step 3 (Gemini Imagen)
# Must have Vertex AI API enabled in GCP project
# Auth: run `gcloud auth application-default login` locally
GOOGLE_CLOUD_PROJECT_ID=your-gcp-project
GOOGLE_CLOUD_LOCATION=us-central1     # default

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/destiny_oracle
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Optional
GCS_BUCKET=destiny-oracle-assets      # default
LOG_PATH=logs                          # default: logs/ next to project
DEFAULT_USER_ID=00000000-0000-0000-0000-000000000001
CORS_ORIGINS=http://localhost:4200
```

> **Without API keys:** the app still runs. Step 1 falls back to template content.
> Step 3 returns placeholder image URLs. Add real keys to unlock the full pipeline.

---

## Running Locally

```bash
# 1. Prerequisites
#    - Java 21 (GraalVM recommended)
#    - PostgreSQL running with destiny_oracle database

# 2. Start with API keys
ANTHROPIC_API_KEY=sk-ant-... \
GOOGLE_CLOUD_PROJECT_ID=your-project \
JAVA_HOME=/path/to/java21 \
mvn spring-boot:run

# 3. Watch pipeline logs
tail -f logs/destiny-oracle.log   # all logs
tail -f logs/pipeline.log         # AI pipeline steps only (Step 1→2→3)
```

**Swagger UI:** http://localhost:8080/swagger-ui.html
**Admin Console:** open `docs/admin-test.html` in browser
**ER Diagram:** open `docs/er-diagram.html` in browser

---

## Database Schema (key tables)

```
destiny_cards                     — one per user per aspect
  └── prompt_status               — NONE | GENERATING | READY | FAILED (tracks Step 2)

card_stage_content                — 6 rows per card (one per stage)
  ├── title                       — written by Step 1 (Claude, from fear+dream)
  ├── tagline                     — written by Step 1
  ├── lore                        — written by Step 1
  └── image_prompt                — written by Step 2 (Claude, from title+lore)

card_images                       — permanent gallery (one per generated image)
  ├── image_url                   — GCS URL written by Step 3 (Gemini)
  └── prompt_summary              — first 200 chars of image prompt used

generation_jobs                   — one per pipeline run
  └── status                      — QUEUED | PROMPTING | IMAGING | COMPLETED | FAILED

generation_job_steps              — 12 rows per job (6 PROMPT + 6 IMAGE)
  ├── phase                       — PROMPT | IMAGE
  ├── stage                       — storm | fog | clearing | aura | radiance | legend
  ├── status                      — WAITING | RUNNING | DONE | FAILED | SKIPPED
  └── message                     — human-readable status shown in the UI
```

---

## Archive / Chapter System (planned)

When a user completes a milestone (e.g. 365-day streak) and wants to continue the same
aspect with a new fear/dream:

1. Old card → `status = ARCHIVED` (images **kept** in gallery permanently)
2. `card_stage_content.image_prompt` → **cleared** (prompts belonged to the old fear)
3. `card_stage_content` title/tagline/lore → **kept** (earned history, not erased)
4. New card created for same aspect with `chapter_number + 1` and new fear/dream
5. Step 1 runs again → fresh narrative arc written from the new input
6. Steps 2+3 run → new images generated for the new chapter

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (LTS, virtual threads) |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL 15+ |
| Schema | Hibernate `ddl-auto: update` (no migration files) |
| ORM | Spring Data JPA / Hibernate |
| Boilerplate | Lombok |
| AI — Stage Content | Spring AI + Anthropic Claude (`claude-3-5-haiku`) |
| AI — Image Prompts | Spring AI + Anthropic Claude (`claude-3-5-haiku`) |
| AI — Images | Google Vertex AI — Gemini Imagen 3 |
| Image Storage | Google Cloud Storage (GCS) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven |

---

## Architecture

```
destiny-oracle-backend/
├── controller/
│   ├── CardController                  CRUD + stage content endpoints
│   └── CardImageGenerationController   Image pipeline + job polling
│
├── service/impl/
│   ├── StageContentGenerationServiceImpl  ★ Step 1 — fear+dream → narrative
│   ├── ImagePromptServiceImpl             ★ Step 2 — narrative → image prompts
│   ├── CardImageGenerationServiceImpl     ★ Step 3 — prompts → images (parallel)
│   ├── JobStepUpdater                     Persists job step transitions (REQUIRES_NEW)
│   ├── GenerationJobServiceImpl           Job polling response builder
│   └── CardServiceImpl                    Card CRUD, triggers Step 1 on addCard()
│
├── entity/
│   ├── DestinyCard                     promptStatus tracks Step 2 state
│   ├── CardStageContent                title/tagline/lore (Step 1) + image_prompt (Step 2)
│   ├── CardImage                       generated image URL + prompt summary (Step 3)
│   ├── GenerationJob                   pipeline run with 12 steps
│   └── GenerationJobStep               individual step with WAITING→DONE lifecycle
│
└── seeder/
    └── DataInitializer                 Seeds 5 demo cards with pre-written content
```
