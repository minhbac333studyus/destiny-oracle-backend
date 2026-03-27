#!/usr/bin/env bash
#
# Destiny Oracle — One-command setup
# Usage: ./scripts/setup.sh
#
# This script sets up the entire backend from scratch on a fresh machine.
# Safe to re-run — skips steps that are already done.
#
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[setup]${NC} $1"; }
ok()   { echo -e "${GREEN}  ✔ $1${NC}"; }
warn() { echo -e "${YELLOW}  ⚠ $1${NC}"; }
fail() { echo -e "${RED}  ✘ $1${NC}"; exit 1; }

# ─── Step 0: Prerequisites ──────────────────────────────────────────────────

log "Checking prerequisites..."

command -v docker >/dev/null 2>&1 || fail "Docker not found. Install: https://www.docker.com/products/docker-desktop/"
docker info >/dev/null 2>&1     || fail "Docker daemon not running. Open Docker Desktop first."
command -v java >/dev/null 2>&1  || warn "Java not found. You'll need Java 21 to run the Spring Boot app."

JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' || echo "0")
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
  warn "Java $JAVA_VERSION detected. Java 21+ recommended."
fi

ok "Prerequisites OK"

# ─── Step 1: .env file ──────────────────────────────────────────────────────

log "Checking .env file..."

if [ ! -f .env ]; then
  cat > .env << 'ENVEOF'
# === REQUIRED ===
ANTHROPIC_API_KEY=sk-ant-REPLACE-ME
POSTGRES_PASSWORD=postgres

# === MEM0 (defaults work fine) ===
MEM0_POSTGRES_PASSWORD=mem0pass
MEM0_NEO4J_PASSWORD=mem0graph

# === OPTIONAL ===
CORS_ORIGINS=http://localhost:4200
# GOOGLE_CLOUD_PROJECT_ID=your-gcp-project    # needed for image generation
ENVEOF
  warn ".env created with placeholders. Edit it with your real ANTHROPIC_API_KEY!"
  warn "  → Get key: https://console.anthropic.com/settings/keys"
  echo ""
  read -p "  Press Enter after editing .env (or press Enter to continue with placeholders)..."
else
  ok ".env already exists"
fi

# ─── Step 2: Start infrastructure ────────────────────────────────────────────

log "Starting infrastructure containers (postgres, ollama, mem0-pgvector, mem0-neo4j)..."
docker compose up -d postgres ollama mem0-pgvector mem0-neo4j 2>&1 | grep -v "WARN"

log "Waiting for containers to be healthy..."
sleep 5

for i in {1..30}; do
  HEALTHY=$(docker compose ps --format json 2>/dev/null | grep -c '"healthy"' || echo "0")
  if [ "$HEALTHY" -ge 2 ]; then
    break
  fi
  echo -n "."
  sleep 2
done
echo ""
ok "Infrastructure containers running"

# ─── Step 3: Pull AI models ─────────────────────────────────────────────────

log "Pulling AI models (first time takes a few minutes)..."

# Check if models already exist
MODELS=$(docker compose exec -T ollama ollama list 2>/dev/null || echo "")

if echo "$MODELS" | grep -q "qwen2.5:1.5b"; then
  ok "qwen2.5:1.5b already pulled"
else
  log "  Pulling qwen2.5:1.5b (~1GB)..."
  docker compose exec -T ollama ollama pull qwen2.5:1.5b
  ok "qwen2.5:1.5b pulled"
fi

if echo "$MODELS" | grep -q "nomic-embed-text"; then
  ok "nomic-embed-text already pulled"
else
  log "  Pulling nomic-embed-text (~270MB)..."
  docker compose exec -T ollama ollama pull nomic-embed-text
  ok "nomic-embed-text pulled"
fi

# ─── Step 4: Start Mem0 ─────────────────────────────────────────────────────

log "Building and starting Mem0 API server..."
docker compose up -d --build mem0 2>&1 | grep -v "WARN"
sleep 5

# Verify mem0
MEM0_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8888/ 2>/dev/null || echo "000")
if [ "$MEM0_STATUS" = "200" ] || [ "$MEM0_STATUS" = "307" ]; then
  ok "Mem0 running at http://localhost:8888"
else
  warn "Mem0 may still be starting. Check: docker compose logs mem0"
fi

# ─── Step 5: Summary ────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Setup complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Services running:"
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | grep -v "WARN"
echo ""
echo "  Next steps:"
echo "    1. Start the Spring Boot app:"
echo "       mvn spring-boot:run"
echo ""
echo "    2. Start the Angular frontend (in another terminal):"
echo "       cd ../destiny-oracle && npm install && ng serve"
echo ""
echo "    3. Open: http://localhost:4200"
echo ""
echo "  Useful URLs:"
echo "    Swagger UI:     http://localhost:8080/swagger-ui.html"
echo "    Mem0 API:       http://localhost:8888/docs"
echo "    Neo4j Browser:  http://localhost:7474  (neo4j / mem0graph)"
echo ""
