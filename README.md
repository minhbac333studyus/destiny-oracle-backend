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
- [Troubleshooting](#troubleshooting)

---

## What Is Destiny Oracle?

Destiny Oracle is a personal growth app that turns life goals into evolving collectible cards — powered by AI.

### How it works

1. **Pick a life aspect** — Health, Career, Finances, Relationships, etc. (up to 10)
2. **Face your fear** — "I'm afraid I'll never feel healthy"
3. **Declare your dream** — "I want to run a marathon by 40"
4. **AI creates your card** — Claude writes a personal narrative. Gemini generates a unique card image.
5. **Check in daily** — Complete 3 AI-generated habits. Your card evolves through 6 stages over 365 days.
6. **Chat with your AI coach** — Get workout plans, meal plans, reminders, and daily insights.

### The 6 stages

```
Storm → Fog → Clearing → Aura → Radiance → Legend
Day 1    Day 31   Day 91     Day 181   Day 271    Day 365
```

Each stage has its own card art, title, tagline, and lore — all generated from YOUR fear and dream.

### Example

> **Aspect:** Health & Body
> **Fear:** "I'm terrified of dying young like my father"
> **Dream:** "I want to run a marathon at 40"
>
> AI generates:
> - **Storm** — "The Body's Betrayal" — dark, rainy card art
> - **Clearing** — "First Light Breaking" — sunrise card art
> - **Legend** — "The Unbreakable" — golden, triumphant card art
>
> Plus 3 daily habits: "30-min walk", "Drink 2L water", "No sugar after 8pm"

### Two modes

| Mode | What it does |
|------|-------------|
| **Card Mode** | Create cards, check in daily, evolve through stages, view art gallery |
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

> **Without API keys:** the app still runs. Card content falls back to templates.
> Image generation returns placeholders. Add real keys to unlock the full AI pipeline.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ANTHROPIC_API_KEY` | Yes | Claude API key for card narrative + image prompts |
| `POSTGRES_PASSWORD` | Yes | App database password |
| `MEM0_POSTGRES_PASSWORD` | No | Mem0 vector DB password (default: `mem0pass`) |
| `MEM0_NEO4J_PASSWORD` | No | Mem0 graph DB password (default: `mem0graph`) |
| `CORS_ORIGINS` | No | Allowed origins (default: `http://localhost:4200`) |
| `GOOGLE_CLOUD_PROJECT_ID` | No | GCP project for Gemini Imagen (image generation) |
| `GOOGLE_CLOUD_LOCATION` | No | GCP region (default: `us-central1`) |
| `MEM0_BASE_URL` | No | Mem0 URL (default: `http://localhost:8888`) |
| `GCS_BUCKET` | No | GCS bucket name (default: `destiny-oracle-assets`) |

---

## API Endpoints

### Cards (core)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/cards` | All user's cards (spread view) |
| POST | `/api/v1/cards` | Create card → auto-triggers AI generation |
| GET | `/api/v1/cards/{id}` | Full card detail with stage content |
| PATCH | `/api/v1/cards/{id}` | Update fear/dream text |
| DELETE | `/api/v1/cards/{id}` | Remove card |
| PUT | `/api/v1/cards/{id}/habits/{habitId}/complete` | Toggle daily habit |

### AI Image Pipeline

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/cards/{id}/generate-stage-content` | Step 1: Claude writes narrative |
| POST | `/api/v1/cards/{id}/generate-images` | Steps 2+3: prompts + images |
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

Three AI calls happen in sequence for every card. **Order is mandatory.**

```
User submits fear + dream
        │
        ▼
  ┌──────────────────────────────────────────┐
  │  STEP 1 — Stage Content (Claude)         │
  │  fear + dream → 6x {title, tagline, lore}│
  │  Auto-runs on card creation              │
  └──────────────────────────────────────────┘
        │
        ▼
  ┌──────────────────────────────────────────┐
  │  STEP 2 — Image Prompts (Claude)         │
  │  title + lore → 6 Imagen-ready prompts   │
  │  Skipped if prompts already exist in DB  │
  └──────────────────────────────────────────┘
        │
        ▼
  ┌──────────────────────────────────────────┐
  │  STEP 3 — Card Images (Gemini Imagen 3)  │
  │  6 images generated in PARALLEL          │
  │  Saved to GCS → card_images table        │
  └──────────────────────────────────────────┘
```

**Key rules:**
- Step 1 before 2 (prompts need narrative context to be personal)
- Step 2 before 3 (Gemini needs text prompt)
- All steps are idempotent (re-running skips already-done work)
- Frontend polls `GET /api/v1/cards/{id}/jobs/latest` every 2-3s for progress

**Pipeline creates a `GenerationJob` with 12 steps** (6 prompt + 6 image). Each step goes through `WAITING → RUNNING → DONE | FAILED | SKIPPED`.

---

## AI Personal Assistant

Beyond card creation, the app includes a full AI coach:

| Feature | How it works |
|---------|-------------|
| **AI Chat** | SSE streaming with Claude. 4-layer context assembly with 4000-token cap. Mem0 stores long-term memories. |
| **Saved Plans** | AI creates workout/meal/routine plans. Saved with versioning and slug lookup. |
| **Tasks + XP** | Plans become tasks with toggleable steps. Completing steps awards XP → cards evolve faster. |
| **Reminders** | Smart reminders with daily/weekly/monthly repeat and snooze. |
| **Nudge Engine** | If you miss a scheduled plan, the app escalates: gentle → firm → urgent (every 5 min check). |
| **Daily Insights** | AI generates a daily summary at 11 PM. Morning push at 8 AM. |

### Memory architecture

```
Priority 1: System prompt               300 tokens
Priority 2: New user message             200 tokens
Priority 3: Saved plan context           300 tokens (if relevant)
Priority 4: Recent 10 raw messages      1500 tokens
Priority 5: Mem0 long-term memories      400 tokens
Priority 6: Session summary              300 tokens
─────────────────────────────────────────────────────
Hard cap:                               4000 tokens  (never exceeded)
```

Mem0 runs locally via Ollama (qwen2.5:1.5b) — **$0 cost** for memory operations.

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

**File:** `mem0/main.py` — change `LLM_MODEL`, or set env var in `docker-compose.yml`

```python
LLM_MODEL = os.environ.get("LLM_MODEL", "qwen2.5:1.5b")  # ← change default here
```

| Model | RAM | Quality | Speed |
|-------|-----|---------|-------|
| `qwen2.5:0.5b` | ~0.5GB | ⭐⭐ | Very fast |
| **`qwen2.5:1.5b`** | ~1.5GB | ⭐⭐⭐ | Fast (current) |
| `qwen2.5:3b` | ~2.5GB | ⭐⭐⭐⭐ | Medium |
| `llama3.2:3b` | ~2.5GB | ⭐⭐⭐⭐ | Medium |
| `qwen2.5:7b` | ~5GB | ⭐⭐⭐⭐⭐ | Slow |

After changing:

```bash
docker compose exec ollama ollama pull <new-model>
docker compose up -d --build mem0
```

### Mem0 Embeddings

**File:** `mem0/main.py` — change `EMBED_MODEL`

| Model | Size | Quality |
|-------|------|---------|
| **`nomic-embed-text`** | 270MB | ⭐⭐⭐⭐ (current) |
| `all-minilm` | 80MB | ⭐⭐⭐ (lighter) |
| `mxbai-embed-large` | 670MB | ⭐⭐⭐⭐⭐ (best) |

> **Warning:** Changing embeddings after memories are stored makes old memories unsearchable.

### Card Generation (Claude)

**File:** `src/main/resources/application.yml`

```yaml
spring.ai.anthropic.chat.options.model: claude-3-5-haiku-latest  # ← change here
```

Options: `claude-3-5-haiku-latest` (cheap) · `claude-3-5-sonnet-latest` (better) · `claude-3-opus-latest` (best)

### Switch Mem0 to cloud API (instead of local Ollama)

**File:** `mem0/main.py` — change `DEFAULT_CONFIG["llm"]`:

```python
# DeepSeek API ($0.14/M tokens)
"llm": {
    "provider": "openai",
    "config": {
        "api_key": os.environ.get("DEEPSEEK_API_KEY"),
        "model": "deepseek-chat",
        "api_base": "https://api.deepseek.com/v1",
    },
},
```

Add the key to `.env` and docker-compose `environment`, then `docker compose up -d --build mem0`.

---

## Database Schema

```
destiny_cards                     — one per user per aspect
  └── prompt_status               — NONE | GENERATING | READY | FAILED

card_stage_content                — 6 rows per card (one per stage)
  ├── title, tagline, lore        — written by Step 1 (Claude)
  └── image_prompt                — written by Step 2 (Claude)

card_images                       — permanent gallery
  ├── image_url                   — GCS URL (Step 3)
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
```

Schema is auto-managed by Hibernate (`ddl-auto: update`). No migration files needed.

---

## Project Structure

```
destiny-oracle-backend/
├── scripts/
│   └── setup.sh                          ★ One-command setup
├── mem0/
│   ├── Dockerfile                        Mem0 container build
│   ├── main.py                           Custom Mem0 server (Ollama config)
│   └── config.yaml                       Mem0 model config
├── docker-compose.yml                    6-container orchestration
│
├── src/main/java/com/destinyoracle/
│   ├── controller/
│   │   ├── CardController                Card CRUD + stage content
│   │   ├── CardImageGenerationController Image pipeline + job polling
│   │   ├── AiChatController              SSE streaming chat
│   │   ├── SavedPlanController           Plan CRUD
│   │   ├── TaskController                Tasks + step toggles
│   │   ├── ReminderController            Reminders CRUD
│   │   ├── InsightController             Daily insights
│   │   └── DeviceController              Push notification tokens
│   │
│   ├── service/impl/
│   │   ├── StageContentGenerationServiceImpl  ★ Step 1 — fear+dream → narrative
│   │   ├── ImagePromptServiceImpl             ★ Step 2 — narrative → image prompts
│   │   ├── CardImageGenerationServiceImpl     ★ Step 3 — prompts → images
│   │   ├── AiChatServiceImpl                  Chat with context assembly
│   │   ├── SavedPlanServiceImpl               Plan management
│   │   ├── TaskServiceImpl                    Task + XP system
│   │   └── ReminderServiceImpl                Reminder scheduling
│   │
│   ├── domain/
│   │   ├── chat/        AiConversation, AiMessage, DailyInsight
│   │   ├── plan/        SavedPlan, PlanSchedule, NudgeState
│   │   ├── task/        Task, TaskStep
│   │   └── notification/ DeviceToken, Reminder
│   │
│   ├── scheduler/
│   │   ├── NudgeScheduler              Proactive nudges (every 5 min)
│   │   ├── ReminderScheduler           Reminder notifications
│   │   ├── InsightScheduler            Daily insight generation (11 PM)
│   │   └── StageProgressionScheduler   Card stage advancement
│   │
│   ├── shared/
│   │   ├── ai/          IntentClassifier, ConversationCompressor
│   │   ├── context/     ContextAssembler, TokenCounter
│   │   ├── event/       Spring events (TaskCompleted, StageAdvanced)
│   │   └── xp/          XpCalculator
│   │
│   └── integration/
│       └── Mem0Client                   HTTP client for Mem0 API
│
└── src/main/resources/
    └── application.yml                  All Spring + AI config
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL 15+ |
| ORM | Spring Data JPA / Hibernate |
| AI — Narrative | Spring AI + Anthropic Claude (Haiku) |
| AI — Images | Google Vertex AI — Gemini Imagen 3 |
| AI — Memory | Mem0 + Ollama (qwen2.5:1.5b) |
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
| **Mem0 Docs** | http://localhost:8888/docs | Mem0 API explorer |
| **Neo4j Browser** | http://localhost:7474 | Memory graph (login: `neo4j`/`mem0graph`) |
| **Pipeline logs** | `tail -f logs/pipeline.log` | AI generation steps in real-time |
| **Mem0 logs** | `docker compose logs -f mem0` | Memory operations |
| **Ollama logs** | `docker compose logs -f ollama` | Model loading + inference |
| **Container stats** | `docker stats` | RAM/CPU per container |
| **Claude usage** | https://console.anthropic.com/settings/usage | API costs |

---

## Cost Breakdown

| Component | Cost | Notes |
|-----------|------|-------|
| Docker containers (all 6) | **$0** | Runs locally |
| Ollama LLM (qwen2.5:1.5b) | **$0** | Local model |
| Ollama embeddings (nomic-embed-text) | **$0** | Local model |
| Mem0 memory operations | **$0** | Uses local Ollama |
| Card narrative (Claude Haiku) | **~$0.003/card** | 1 API call per card |
| Image prompts (Claude Haiku) | **~$0.001/card** | 1 API call per card |
| Card images (Gemini Imagen 3) | **~$0.04/card** | 6 images per card |
| Unit tests | **$0** | No API calls |
| **Typical monthly (active user)** | **~$1-3/mo** | 10 cards + daily chat |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Cannot connect to Docker daemon` | Open Docker Desktop first |
| `model requires more system memory` | Use a smaller model: `qwen2.5:1.5b` instead of `8b` |
| `Empty reply from server` (mem0) | Check `docker compose logs mem0` — usually missing `ollama` pip package |
| `OPENAI_API_KEY not set` (mem0) | Mem0 not reading custom `main.py` — run `docker compose up -d --build mem0` |
| `GOOGLE_CLOUD_PROJECT_ID warning` | Harmless warning, ignore it. Add `GOOGLE_CLOUD_PROJECT_ID=unused` to `.env` to silence |
| Mem0 slow (10+ seconds) | Normal for small CPU. Mem0 runs async — user doesn't wait |
| `Port already in use` | `docker compose down` first, then restart |
| Java version mismatch | Need Java 21+: `brew install openjdk@21` |
