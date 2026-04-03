# Destiny Oracle — Backend

> Turn self-improvement into a collectible card game with AI-powered personal coaching.

Spring Boot 3.3 · Java 21 · PostgreSQL · Claude AI · Gemini Imagen 3 · Mem0 · Ollama · Docker

---

## Table of Contents

- [What Is Destiny Oracle?](#what-is-destiny-oracle)
- [Quick Start (New Server)](#quick-start-new-server)
- [Manual Setup (Step by Step)](#manual-setup-step-by-step)
- [Environment Variables](#environment-variables)
- [API Endpoints](#api-endpoints)
- [AI Pipeline — How Cards Are Generated](#ai-pipeline--how-cards-are-generated)
- [AI Personal Assistant](#ai-personal-assistant)
- [Docker Architecture](#docker-architecture)
- [Switching AI Models](#switching-ai-models)
- [Database Schema](#database-schema)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Monitoring & Debugging](#monitoring--debugging)
- [Cost Breakdown](#cost-breakdown)
- [Recent Changes](#recent-changes)
- [Troubleshooting](#troubleshooting)

---

## What Is Destiny Oracle?

Destiny Oracle is a personal growth app that turns life goals into evolving collectible cards — powered by AI.

### How it works

1. **Pick a life aspect** — Health, Career, Finances, Relationships, etc. (or create custom ones)
2. **Face your fear** — "I'm afraid I'll never feel healthy"
3. **Declare your dream** — "I want to run a marathon by 40"
4. **AI creates your card** — Claude writes image prompts personalized to your fear. Gemini generates unique card art for all 6 stages.
5. **Evolve through stages** — Your card transforms through 6 visual stages over 365 days.
6. **Chat with your AI coach** — Get workout plans, meal plans, reminders, and daily insights.

### The 6 stages

```
Storm → Fog → Clearing → Aura → Radiance → Legend
Day 1    Day 31   Day 91     Day 181   Day 271    Day 365
```

Each stage has its own card art — generated from YOUR fear text with escalating visual themes (dark storm → golden legend).

### Example

> **Aspect:** Health & Body
> **Fear:** "I'm terrified of dying young like my father"
> **Dream:** "I want to run a marathon at 40"
>
> AI generates 6 card images:
> - **Storm** — dark rainy scene, character curled up, crowd turning away
> - **Clearing** — golden sunlight, character taking first steps outside
> - **Legend** — sakura petals, character standing triumphant before inspired crowd

### Two modes

| Mode | What it does |
|------|-------------|
| **Card Mode** | Create cards, evolve through stages, view art gallery |
| **AI Assistant** | Chat with AI coach, get plans/tasks/reminders, earn XP, receive nudges |

---

## Quick Start (New Server)

**Prerequisites:** Docker Desktop + Java 21

```bash
# 1. Clone
git clone https://github.com/minhbac333studyus/destiny-oracle-backend.git
cd destiny-oracle-backend

# 2. Run the setup script (does everything)
./scripts/setup.sh

# 3. Start the app
mvn spring-boot:run

# 4. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

The setup script will:
- Check prerequisites (Docker, Java)
- Create `.env` with placeholders (you fill in your API key)
- Start all Docker containers (postgres, ollama, mem0, neo4j, pgvector)
- Pull AI models (qwen2.5:1.5b + nomic-embed-text)
- Build and start Mem0
- Verify everything is running

**Safe to re-run** — skips steps already done.

---

## Manual Setup (Step by Step)

If you prefer to do it yourself instead of using `setup.sh`:

### 1. Install prerequisites

- **Docker Desktop**: https://www.docker.com/products/docker-desktop/
- **Java 21**: `brew install openjdk@21` (Mac) or https://adoptium.net
- Verify: `docker --version` && `java -version`

### 2. Create `.env`

```bash
cat > .env << 'EOF'
ANTHROPIC_API_KEY=sk-ant-...your-key...
POSTGRES_PASSWORD=postgres
MEM0_POSTGRES_PASSWORD=mem0pass
MEM0_NEO4J_PASSWORD=mem0graph
CORS_ORIGINS=http://localhost:4200
EOF
```

Get your API key at https://console.anthropic.com/settings/keys

### 3. Start infrastructure

```bash
docker compose up -d postgres ollama mem0-pgvector mem0-neo4j
docker compose ps                                         # wait until healthy
```

### 4. Pull AI models (one-time, ~1.3GB total)

```bash
docker compose exec ollama ollama pull qwen2.5:1.5b       # Mem0 LLM
docker compose exec ollama ollama pull nomic-embed-text    # embeddings
```

### 5. Start Mem0

```bash
docker compose up -d --build mem0
curl http://localhost:8888/                                # verify — should respond
```

### 6. Start the app

```bash
mvn spring-boot:run
```

### 7. Start the frontend (separate terminal)

```bash
cd ../destiny-oracle
npm install
ng serve              # http://localhost:4200
```

> **Without API keys:** the app still runs. Image generation returns placeholders. Add real keys to unlock the full AI pipeline.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ANTHROPIC_API_KEY` | Yes | Claude API key for image prompts + chat |
| `POSTGRES_PASSWORD` | Yes | App database password |
| `MEM0_POSTGRES_PASSWORD` | No | Mem0 vector DB password (default: `mem0pass`) |
| `MEM0_NEO4J_PASSWORD` | No | Mem0 graph DB password (default: `mem0graph`) |
| `CORS_ORIGINS` | No | Allowed origins (default: `http://localhost:4200`) |
| `GOOGLE_CLOUD_PROJECT_ID` | No | GCP project for Gemini Imagen (image generation) |
| `GOOGLE_CLOUD_LOCATION` | No | GCP region (default: `us-central1`) |
| `MEM0_BASE_URL` | No | Mem0 URL (default: `http://localhost:8888`) |

---

## API Endpoints

### Cards

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/cards` | All user's cards (spread view) |
| POST | `/api/v1/cards` | Create card → auto-triggers image pipeline |
| GET | `/api/v1/cards/{id}` | Full card detail with stats |
| PATCH | `/api/v1/cards/{id}` | Update fear/dream text, label, icon |
| DELETE | `/api/v1/cards/{id}` | Remove card |

### AI Image Pipeline

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/cards/{id}/generate-images` | Generate prompts + images (12-step job) |
| POST | `/api/v1/cards/{id}/generate-images/{stage}` | Regenerate one stage |
| GET | `/api/v1/cards/{id}/jobs/latest` | Poll generation progress |

### AI Chat

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/chat/stream` | SSE streaming chat with AI |
| GET | `/api/v1/chat/conversations` | List conversations |
| GET | `/api/v1/chat/conversations/{id}` | Get conversation history |
| DELETE | `/api/v1/chat/conversations/{id}` | Delete conversation |

### Plans, Tasks, Reminders

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/plans` | List saved plans |
| POST | `/api/v1/plans` | Save a new plan |
| GET | `/api/v1/tasks/active` | Active tasks with steps |
| PATCH | `/api/v1/tasks/steps/{id}/toggle` | Toggle task step |
| GET | `/api/v1/reminders` | List reminders |
| POST | `/api/v1/reminders` | Create reminder |
| PATCH | `/api/v1/reminders/{id}/snooze` | Snooze reminder |
| GET | `/api/v1/insights/today` | Today's AI insight |

### Users

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/users/{id}` | Get user profile |
| PUT | `/api/v1/users/{id}` | Update user |
| POST | `/api/v1/users/{id}/avatar` | Upload avatar |
| POST | `/api/v1/users/{id}/generate-chibi` | AI chibi avatar |

**Full interactive docs:** http://localhost:8080/swagger-ui.html

---

## AI Pipeline — How Cards Are Generated

**One Claude call + one Gemini call per card.** Runs asynchronously — card creation returns instantly.

```
User creates card (POST /api/v1/cards)
        │
        ▼  CardServiceImpl saves card → publishes CardCreatedEvent
        │
        ▼  CardEventListener (async thread)
        │
  ┌──────────────────────────────────────────────┐
  │  PHASE 1 — Image Prompts (Claude Haiku)      │
  │                                              │
  │  Input:  fear text + aspect label            │
  │  Context: stage moods + pose/outfit/crowd    │
  │           specs (hardcoded per stage)         │
  │  Output: 6 detailed image prompts            │
  │  Saves:  card_stage_content.image_prompt     │
  │                                              │
  │  Cached: skips Claude if all 6 already exist │
  └──────────────────────────────────────────────┘
        │
        ▼  Gate: all 6 prompts must be present
        │
  ┌──────────────────────────────────────────────┐
  │  PHASE 2 — Card Images (Gemini Imagen 3)     │
  │                                              │
  │  6 images generated sequentially             │
  │  Base64 → detect format → save to disk       │
  │  Path: /generated/{userId}/{aspect}-{stage}  │
  └──────────────────────────────────────────────┘
```

### How prompts are personalized

Each image prompt is built from 3 layers:

1. **User's fear text** — injected directly into the meta-prompt + CROWD RULE tells Claude to derive crowd behavior from fear
2. **Stage visual specs** (hardcoded) — pose, outfit, crowd count, scene description per stage
3. **Stage moods** (hardcoded) — color palette + atmosphere per stage

```
Storm:     dark stormy atmosphere, cold blues, oppressive shadows
Fog:       thick pale mist, muted lavender, dreamlike ambiguity
Clearing:  golden sunlight breaking through, warm amber, first clarity
Aura:      ethereal energy field, purple-blue glow, serene confidence
Radiance:  brilliant golden radiance, warm sparks, luminous character
Legend:    sakura petals, celestial twilight, timeless legendary aura
```

### Job tracking (12 steps)

The pipeline creates a `GenerationJob` with 12 steps for real-time UI polling:

```
Steps 0-5:  PROMPT phase (one per stage) — WAITING → RUNNING → DONE/SKIPPED
Steps 6-11: IMAGE phase (one per stage)  — WAITING → RUNNING → DONE/FAILED
```

Frontend polls `GET /api/v1/cards/{id}/jobs/latest` every 2-3 seconds.

---

## AI Personal Assistant

Beyond card creation, the app includes a full AI coach:

| Feature | How it works |
|---------|-------------|
| **AI Chat** | SSE streaming with Claude. 6-layer context assembly with 4500-token cap. Mem0 stores long-term memories. |
| **Saved Plans** | AI creates workout/meal/routine plans. Saved with versioning and slug lookup. |
| **Tasks + XP** | Plans become tasks with toggleable steps. Completing steps awards XP. |
| **Reminders** | Smart reminders with daily/weekly/monthly repeat and snooze. |
| **Nudge Engine** | If you miss a scheduled plan, the app escalates: gentle → firm → urgent (every 5 min check). |
| **Daily Insights** | AI generates a daily summary at 11 PM. Morning push at 8 AM. |

### Chat context assembly (6 layers)

```
Layer 1: System prompt               300 tokens  (+ 300 if TASK/REMINDER intent)
Layer 2: New user message             200 tokens
Layer 3: Saved plan context           300 tokens  (if message mentions a plan)
Layer 4: Recent 4 raw messages        800 tokens
Layer 5: Mem0 long-term memories      300 tokens  (8s timeout, non-blocking)
Layer 6: Compressed summaries         300 tokens
─────────────────────────────────────────────────
Hard cap:                            4500 tokens  (never exceeded)
```

### Intent-driven prompt chaining

`IntentClassifier` (regex-based) detects intent from the user message:

| Intent | Trigger | System prompt addition |
|--------|---------|----------------------|
| TASK | "create task", "plan for", "workout" | Adds `[ACTION]{json}[/ACTION]` block instructions |
| REMINDER | "remind me", "set alarm" | Same — backend parses and creates reminder |
| PLAN_SAVE | "save this plan" | Same — backend saves the plan |
| GENERAL | Everything else | No action addendum (saves ~300 tokens) |

### Conversation compression

Automatic when conversation reaches 20+ uncompressed messages:
- Keeps recent 10 messages raw
- Compresses older messages into bullet-point summary via Claude
- Saves ~70% tokens per compression round
- Pre-truncates each message to 300 chars before sending to Claude
- Runs async — user never waits

---

## Docker Architecture

6 containers, one command:

```
┌─────────────────────────────────────────────────────────────┐
│  destiny-oracle (Spring Boot)        ← port 8080            │
│       │                                                      │
│       ├── postgres (app database)    ← port 5432             │
│       │                                                      │
│       └── mem0 (AI memory API)       ← port 8888             │
│             │                                                │
│             ├── ollama (local LLM + embeddings)               │
│             │     └── port 11434                              │
│             ├── mem0-pgvector (vector store)                   │
│             │     └── port 8432                               │
│             └── mem0-neo4j (graph store)                       │
│                   └── port 7474 (web) + 7687 (bolt)           │
└─────────────────────────────────────────────────────────────┘
```

### Common commands

```bash
docker compose up -d                   # start everything
docker compose down                    # stop everything
docker compose down -v                 # stop + delete all data
docker compose ps                      # check status
docker compose logs -f mem0            # watch mem0 logs
docker compose restart mem0            # restart one service
docker stats                           # monitor RAM/CPU
```

---

## Switching AI Models

All AI config is centralized. Here's how to change any model.

### Mem0 LLM (summarization & fact extraction)

**Current: Claude Haiku via Anthropic API**

**File:** `mem0/main.py` — change `LLM_MODEL`, or set env var `MEM0_LLM_MODEL` in `docker-compose.yml`

| Provider | Model | Speed | Cost |
|----------|-------|-------|------|
| **Anthropic** | **`claude-haiku-4-5-20251001`** | **~1.7s search** | **~$0.002/msg (current)** |
| Anthropic | `claude-sonnet-4-20250514` | ~2s search | ~$0.01/msg |
| Ollama (local) | `qwen2.5:1.5b` | 2–5 min search | $0 (CPU-bound) |

After changing:
```bash
docker compose up -d --build mem0
```

### Mem0 Embeddings

**File:** `mem0/main.py` — change `EMBED_MODEL`

| Model | Size | Quality |
|-------|------|---------|
| **`nomic-embed-text`** | 270MB | Good (current) |
| `all-minilm` | 80MB | Lighter |
| `mxbai-embed-large` | 670MB | Best |

> **Warning:** Changing embeddings after memories are stored makes old memories unsearchable.

### Card Image Prompts (Claude)

**File:** `src/main/resources/application.yml`

```yaml
spring.ai.anthropic.chat.options.model: claude-3-5-haiku-latest
```

Options: `claude-3-5-haiku-latest` (cheap) · `claude-3-5-sonnet-latest` (better) · `claude-3-opus-latest` (best)

---

## Database Schema

```
destiny_cards                     — one per user per aspect
  └── prompt_status               — NONE | GENERATING | READY | FAILED

card_stage_content                — 6 rows per card (one per stage)
  └── image_prompt                — written by Claude (Phase 1)

card_images                       — permanent gallery
  ├── image_url                   — local path or GCS URL (Phase 2)
  └── prompt_summary              — first 200 chars of prompt

generation_jobs                   — one per pipeline run
generation_job_steps              — 12 rows per job (6 PROMPT + 6 IMAGE)

ai_conversations / ai_messages    — chat history
conversation_memory               — compressed summaries
daily_insight                     — AI-generated daily summaries

saved_plan / plan_schedule        — workout/meal/routine plans
task / task_step                  — tasks with toggleable steps
reminder                          — smart reminders with repeat
nudge_state                       — escalation tracking
device_token                      — push notification tokens
notification_rules                — smart notification rules with cached steps
activity_log                      — parsed activity entries
```

Schema is auto-managed by Hibernate (`ddl-auto: update`). No migration files needed.

---

## Project Structure

```
destiny-oracle-backend/
├── scripts/
│   └── setup.sh                          One-command setup
├── mem0/
│   ├── Dockerfile                        Mem0 container build
│   ├── main.py                           Custom Mem0 server (Anthropic LLM + Ollama embeddings)
│   └── config.yaml                       Mem0 model config
├── docker-compose.yml                    6-container orchestration
│
├── src/main/java/com/destinyoracle/
│   │
│   ├── domain/                           ★ Domain-Driven Design — all business logic
│   │   ├── card/
│   │   │   ├── controller/               CardController, CardImageGenerationController, AspectDefinitionController
│   │   │   ├── entity/                   DestinyCard, CardStageContent, CardImage, GenerationJob, CardStage...
│   │   │   ├── event/                    CardCreatedEvent, CardEventListener (async image pipeline)
│   │   │   ├── repository/               DestinyCardRepository, CardStageContentRepository...
│   │   │   └── service/impl/             CardServiceImpl, ImagePromptServiceImpl,
│   │   │                                 CardImageGenerationServiceImpl, GeminiImageProvider...
│   │   ├── chat/
│   │   │   ├── controller/               AiChatController, InsightController
│   │   │   ├── entity/                   AiConversation, AiMessage, ConversationMemory, DailyInsight
│   │   │   ├── repository/               AiConversationRepository, AiMessageRepository...
│   │   │   └── service/impl/             AiChatServiceImpl (SSE streaming + context assembly)
│   │   │
│   │   ├── notification/
│   │   │   ├── controller/               ReminderController, DeviceController, NotificationRuleController
│   │   │   ├── entity/                   Reminder, DeviceToken, NotificationRule, ActivityLog
│   │   │   ├── repository/
│   │   │   └── service/impl/             ReminderServiceImpl, PushNotificationServiceImpl, DeviceTokenServiceImpl
│   │   │
│   │   ├── plan/
│   │   │   ├── controller/               SavedPlanController
│   │   │   ├── entity/                   SavedPlan, PlanSchedule, NudgeState
│   │   │   ├── repository/
│   │   │   └── service/impl/             SavedPlanServiceImpl
│   │   │
│   │   ├── task/
│   │   │   ├── controller/               TaskController
│   │   │   ├── entity/                   Task, TaskStep
│   │   │   ├── repository/
│   │   │   └── service/impl/             TaskServiceImpl
│   │   │
│   │   └── user/
│   │       ├── controller/               UserController, AuthController
│   │       ├── entity/                   AppUser
│   │       ├── repository/               UserRepository
│   │       └── service/impl/             UserServiceImpl
│   │
│   ├── config/                           AiClientConfig, AiRoutingConfig, AppProperties, CorsConfig...
│   ├── controller/                       AdminDebugController, MonitorController (cross-cutting)
│   ├── dto/
│   │   ├── request/                      AddCardRequest, ChatMessageRequest, SavePlanRequest...
│   │   └── response/                     CardDetailResponse, ConversationResponse, TaskResponse...
│   ├── exception/                        GlobalExceptionHandler, ResourceNotFoundException
│   ├── integration/                      Mem0Client (HTTP client for Mem0 API)
│   ├── scheduler/                        NudgeScheduler, ReminderScheduler, InsightScheduler,
│   │                                     SmartNotificationScheduler, StageProgressionScheduler
│   ├── seeder/                           DataInitializer (demo data)
│   ├── service/                          SystemHealthService (cross-cutting)
│   └── shared/
│       ├── ai/                           IntentClassifier, ConversationCompressor
│       ├── context/                      ContextAssembler, TokenCounter, AssembledContext
│       ├── event/                        TaskCompletedEvent, ReminderCompletedEvent, StageAdvancedEvent
│       └── xp/                           XpCalculator
│
└── src/main/resources/
    └── application.yml                   All Spring + AI config
```

**~160 Java files** across 6 domain packages.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL 15+ |
| ORM | Spring Data JPA / Hibernate |
| AI — Image Prompts | Spring AI 1.1.4 + Anthropic Claude Haiku |
| AI — Chat | Spring AI + Anthropic Claude (with prompt caching) |
| AI — Images | Google Vertex AI — Gemini Imagen 3 |
| AI — Memory | Mem0 + Claude Haiku (LLM) + Ollama (embeddings) |
| AI — Embeddings | Ollama (nomic-embed-text) |
| Graph Store | Neo4j 5 |
| Vector Store | pgvector |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven |

---

## Monitoring & Debugging

| Tool | URL / Command | What it shows |
|------|---------------|---------------|
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Interactive API docs |
| **Admin Debug** | `GET /api/v1/admin/cards/{id}` | Full card dump: prompts, images, pipeline status |
| **System Health** | `GET /api/v1/admin/system-health` | All services status + metrics |
| **Mem0 Docs** | http://localhost:8888/docs | Mem0 API explorer |
| **Neo4j Browser** | http://localhost:7474 | Memory graph (login: `neo4j`/`mem0graph`) |
| **Mem0 logs** | `docker compose logs -f mem0` | Memory operations |
| **Ollama logs** | `docker compose logs -f ollama` | Model loading + inference |
| **Container stats** | `docker stats` | RAM/CPU per container |
| **Claude usage** | https://console.anthropic.com/settings/usage | API costs |

---

## Cost Breakdown

| Component | Cost | Notes |
|-----------|------|-------|
| Docker containers (all 6) | **$0** | Runs locally |
| Ollama embeddings (nomic-embed-text) | **$0** | Local model |
| Mem0 memory operations | **~$0.002/msg** | Claude Haiku for LLM, Ollama for embeddings |
| Image prompts (Claude Haiku) | **~$0.001/card** | 1 API call per card (6 prompts) |
| Card images (Gemini Imagen 3) | **~$0.04/card** | 6 images per card |
| Chat (Claude Haiku) | **~$0.001/msg** | With prompt caching (90% cheaper on repeat) |
| **Typical monthly (active user)** | **~$2-4/mo** | 10 cards + daily chat + Mem0 |

---

## Recent Changes

### Architecture Refactor: Full Domain-Driven Design

**Before:** Mixed package structure — entities scattered across `entity/`, `domain/chat/entity/`, repositories in `repository/` and `domain/*/repository/`, services split between `service/card/` and `service/chat/`.

**After:** Every domain is a self-contained vertical slice:

```
domain/{name}/
├── entity/
├── repository/
├── service/impl/
└── controller/
```

6 domains: `card`, `chat`, `notification`, `plan`, `task`, `user`.

Cross-cutting concerns remain at top level: `config/`, `shared/`, `scheduler/`, `integration/`, `exception/`.

### Simplified Card Pipeline: 2 Claude Calls → 1

**Removed:** `StageContentGenerationService` — an extra Claude call that generated "action scenes" (one-sentence descriptions per stage). These were fed into the image prompt generator as context.

**Why:** `ImagePromptServiceImpl` already has detailed hardcoded stage specs (pose, outfit, crowd, scene) + stage moods + the user's fear text + CROWD RULE. The action scene added marginal personalization for a full extra Claude API call.

**Impact:** Card creation now makes 1 Claude call (image prompts) instead of 2.

### Removed Features

| Feature | Files removed | Reason |
|---------|--------------|--------|
| **Habits** | Entity, repo, DTO, completion tracking | Simplified card model |
| **Goals / Milestones** | Entity, repo, DTO, service, controller (11 files) | Replaced by task system |
| **Image history** | `@OneToMany imageHistory` field on DestinyCard | Not needed — `card_images` table still exists |
| **Title / Tagline / Lore** | Fields from CardStageContent entity + DTOs | Only `image_prompt` is needed per stage |
| **Action scene** | Field from CardStageContent + StageContentGeneration service | Redundant with stage specs in ImagePromptService |

### Token Optimizations

10 optimizations applied to reduce LLM costs:

1. **Gated memory extraction** — only calls Mem0 when message > 30 chars AND intent != GENERAL
2. **Compressed extract prompt** — 16 lines → 2 lines
3. **Merged system messages** — 4 separate SystemMessage objects → 1 concatenated block (better prompt cache hit rate)
4. **Stripped text block indentation** — removed leading whitespace from system prompt
5. **Compressed ACTION addendum** — 18 lines → 5 lines compact format
6. **Pre-truncation in compression** — each message truncated to 300 chars before Claude summarization
7. **Cached notification steps** — reuses AI-generated task steps instead of calling Claude every notification cycle
8. **Trimmed stage specs** — removed verbose per-stage examples from image prompt meta-prompt
9. **Shared CROWD RULE preamble** — one rule instead of per-stage crowd instructions
10. **Intent gating** — ACTION addendum only injected for TASK/REMINDER intents (saves ~300 tokens on GENERAL messages)

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Cannot connect to Docker daemon` | Open Docker Desktop first |
| `model requires more system memory` | Use a smaller model: `qwen2.5:1.5b` instead of `8b` |
| `Empty reply from server` (mem0) | Check `docker compose logs mem0` — usually missing pip package |
| Mem0 slow on add (~6-8s) | Expected with graph store. Memory add runs in background, user doesn't wait |
| Chat freezes | Check if Mem0 search is timing out — grep logs for "timed out after 8s" |
| Health endpoint slow | All checks run in parallel with 12s max timeout. Check individual service connectivity |
| Empty `ANTHROPIC_API_KEY` in Docker | Shell env overrides `.env`. Use: `set -a && source .env && set +a` before `docker compose up` |
| `Port already in use` | `docker compose down` first, then restart |
| Java version mismatch | Need Java 21+: `brew install openjdk@21` |
| Mem0 `tool_use` errors | Anthropic SDK format changed — check monkey-patch in `mem0/main.py` |
