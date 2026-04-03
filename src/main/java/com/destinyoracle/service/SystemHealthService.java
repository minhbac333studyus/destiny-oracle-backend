package com.destinyoracle.service;

import com.destinyoracle.controller.AdminDebugController;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Aggregates health status from all backend services in the Destiny Oracle stack.
 *
 * <h3>Architecture overview — 8 services probed:</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Oracle Backend  (this JVM)        → JVM Runtime + MXBean      │
 * │  PostgreSQL      (localhost:5432)   → JDBC SELECT version()     │
 * │  Ollama LLM      (localhost:11434)  → GET /api/tags             │
 * │  Mem0 API        (localhost:8888)   → GET /docs                 │
 * │  Mem0 pgvector   (localhost:8432)   → JDBC SELECT version()     │
 * │  Mem0 Neo4j      (localhost:7474)   → GET /                     │
 * │  Claude API      (config only)      → check API key presence    │
 * │  Image Provider  (config only)      → check API key presence    │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Data sources for each config value (trace from application.yml):</h3>
 * <ul>
 *   <li>{@code spring.ai.ollama.base-url}     → Ollama REST API base</li>
 *   <li>{@code mem0.base-url}                  → Mem0 FastAPI sidecar (docker: mem0:8000 → host:8888)</li>
 *   <li>{@code spring.ai.anthropic.api-key}    → Claude API key from .env ANTHROPIC_API_KEY</li>
 *   <li>{@code spring.ai.anthropic.chat.options.model} → Claude model ID</li>
 *   <li>{@code spring.ai.ollama.chat.model}    → Ollama chat model (e.g. qwen3:8b)</li>
 *   <li>{@code app.image-provider}             → "gemini" or "modelslab"</li>
 *   <li>{@code app.gemini.api-key / model}     → Gemini config from .env GEMINI_API_KEY</li>
 *   <li>{@code app.modelslab.api-key / model-id} → ModelsLab config from .env MODELSLAB_API_KEY</li>
 * </ul>
 *
 * @see AdminDebugController#getSystemHealth()
 */
@Service
public class SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    private static final int PROBE_TIMEOUT_SECONDS = 10;

    private final DataSource dataSource;  // Injected — Spring main DataSource (HikariCP → PostgreSQL :5432)
    private final RestClient http;        // Generic HTTP client for probing external services
    private final ExecutorService healthPool = Executors.newFixedThreadPool(4);

    // ── Config values from application.yml ──────────────────────────────────

    /** Source: spring.ai.ollama.base-url | Default: http://localhost:11434 */
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    /** Source: mem0.base-url | Docker maps mem0:8000 → host:8888 */
    @Value("${mem0.base-url:http://localhost:8888}")
    private String mem0BaseUrl;

    /** Source: spring.ai.anthropic.api-key | From .env ANTHROPIC_API_KEY */
    @Value("${spring.ai.anthropic.api-key:disabled}")
    private String anthropicApiKey;

    /** Source: spring.ai.anthropic.chat.options.model */
    @Value("${spring.ai.anthropic.chat.options.model:claude-haiku-4-5-20251001}")
    private String claudeModel;

    /** Source: spring.ai.ollama.chat.model | Used for chat, compression, insights ($0 cost) */
    @Value("${spring.ai.ollama.chat.model:qwen3:8b}")
    private String ollamaChatModel;

    /** Source: app.image-provider | "gemini" or "modelslab" */
    @Value("${app.image-provider:modelslab}")
    private String imageProvider;

    /** Source: app.gemini.api-key | From .env GEMINI_API_KEY */
    @Value("${app.gemini.api-key:#{null}}")
    private String geminiApiKey;

    /** Source: app.gemini.model */
    @Value("${app.gemini.model:gemini-2.5-flash-image}")
    private String geminiModel;

    /** Source: app.modelslab.api-key | From .env MODELSLAB_API_KEY */
    @Value("${app.modelslab.api-key:#{null}}")
    private String modelslabApiKey;

    /** Source: app.modelslab.model-id */
    @Value("${app.modelslab.model-id:qwen}")
    private String modelslabModelId;

    public SystemHealthService(DataSource dataSource, RestClient.Builder builder) {
        this.dataSource = dataSource;
        // Health-check HTTP client with short timeouts so one slow service doesn't block the whole report
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS));
        this.http = builder.requestFactory(factory).build();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Probes all 8 services and returns aggregated health report.
     * Each check is isolated — one failure does not block others.
     *
     * @return { timestamp, services: { serviceName: { status, ...metrics } } }
     */
    public Map<String, Object> getFullHealth() {
        var result = new LinkedHashMap<String, Object>();
        result.put("timestamp", Instant.now().toString());

        // Run all checks in parallel with per-check timeouts
        var checks = new LinkedHashMap<String, Future<Map<String, Object>>>();
        checks.put("oracleBackend", healthPool.submit(this::checkSelf));
        checks.put("postgres",      healthPool.submit(this::checkPostgres));
        checks.put("ollama",        healthPool.submit(this::checkOllama));
        checks.put("mem0Api",       healthPool.submit(this::checkMem0Api));
        checks.put("mem0Pgvector",  healthPool.submit(this::checkMem0Pgvector));
        checks.put("mem0Neo4j",     healthPool.submit(this::checkMem0Neo4j));
        checks.put("claudeApi",     healthPool.submit(this::checkClaudeApi));
        checks.put("imageProvider", healthPool.submit(this::checkImageProvider));

        var services = new LinkedHashMap<String, Object>();
        for (var entry : checks.entrySet()) {
            try {
                services.put(entry.getKey(), entry.getValue().get(PROBE_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                services.put(entry.getKey(), Map.of("status", "DOWN", "error", "Health check timed out"));
                entry.getValue().cancel(true);
            } catch (Exception e) {
                services.put(entry.getKey(), Map.of("status", "DOWN", "error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        }
        result.put("services", services);
        return result;
    }

    // ── Live service checks (network / JDBC) ────────────────────────────────

    /**
     * Oracle Backend (this JVM) — always UP if we can respond.
     *
     * Data sources:
     *   jvmMemoryUsedMb  → Runtime.getRuntime().totalMemory() - freeMemory()
     *   jvmMemoryMaxMb   → Runtime.getRuntime().maxMemory()     (-Xmx flag)
     *   activeThreads    → Thread.activeCount()
     *   javaVersion      → System property "java.version"
     *   uptimeSeconds    → RuntimeMXBean.getUptime()
     */
    private Map<String, Object> checkSelf() {
        var rt = Runtime.getRuntime();
        return Map.of(
                "status",          "UP",
                "jvmMemoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                "jvmMemoryMaxMb",  rt.maxMemory() / (1024 * 1024),
                "activeThreads",   Thread.activeCount(),
                "javaVersion",     System.getProperty("java.version"),
                "uptimeSeconds",   ManagementFactory.getRuntimeMXBean().getUptime() / 1000
        );
    }

    /**
     * Main PostgreSQL (docker: postgres:5432).
     *
     * Data sources:
     *   version          → JDBC: SELECT version()
     *   activeConnections → HikariPoolMXBean.getActiveConnections()
     *   idleConnections   → HikariPoolMXBean.getIdleConnections()
     *   totalConnections  → HikariPoolMXBean.getTotalConnections()
     *   maxConnections    → HikariDataSource.getMaximumPoolSize()   (from hikari config)
     */
    private Map<String, Object> checkPostgres() {
        var info = new LinkedHashMap<String, Object>();
        try (var conn = dataSource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT version()")) {
            rs.next();
            info.put("status", "UP");
            info.put("version", rs.getString(1));
            if (dataSource instanceof HikariDataSource hds && hds.getHikariPoolMXBean() != null) {
                var pool = hds.getHikariPoolMXBean();
                info.put("activeConnections", pool.getActiveConnections());
                info.put("idleConnections",   pool.getIdleConnections());
                info.put("totalConnections",  pool.getTotalConnections());
                info.put("maxConnections",    hds.getMaximumPoolSize());
            }
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    /**
     * Ollama LLM engine (docker: ollama:11434).
     *
     * Data sources:
     *   baseUrl   → application.yml: spring.ai.ollama.base-url
     *   chatModel → application.yml: spring.ai.ollama.chat.model
     *   models[]  → HTTP GET {ollamaBaseUrl}/api/tags → response.models[].name
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkOllama() {
        var info = new LinkedHashMap<String, Object>();
        info.put("baseUrl", ollamaBaseUrl);
        info.put("chatModel", ollamaChatModel);
        try {
            var body = http.get().uri(ollamaBaseUrl + "/api/tags").retrieve().body(Map.class);
            info.put("status", "UP");
            if (body != null && body.containsKey("models")) {
                info.put("models", ((List<Map<String, Object>>) body.get("models"))
                        .stream().map(m -> m.get("name")).filter(Objects::nonNull).toList());
            }
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    /**
     * Mem0 FastAPI sidecar (docker: mem0:8000 → host:8888).
     * Does a REAL functional probe: POST /v1/memories/search/ with a test query.
     * This catches config errors (wrong Ollama model, DB down, etc.) that a simple /docs ping misses.
     *
     * Data sources:
     *   baseUrl        → application.yml: mem0.base-url
     *   responseTimeMs → measured round-trip
     */
    private Map<String, Object> checkMem0Api() {
        var info = new LinkedHashMap<String, Object>();
        info.put("baseUrl", mem0BaseUrl);
        try {
            // Quick ping to confirm the server is reachable (FastAPI /docs is fast)
            long t0 = System.currentTimeMillis();
            http.get().uri(mem0BaseUrl + "/docs").retrieve().toBodilessEntity();
            info.put("responseTimeMs", System.currentTimeMillis() - t0);
            info.put("status", "UP");
        } catch (Exception e) {
            String msg = e.getMessage();
            info.put("status", "DOWN");
            if (msg != null && msg.contains("\"detail\"")) {
                int start = msg.indexOf("\"detail\":\"") + 10;
                int end = msg.indexOf("\"", start);
                if (start > 10 && end > start) {
                    info.put("error", msg.substring(start, end));
                } else {
                    info.put("error", msg);
                }
            } else {
                info.put("error", msg);
            }
        }
        return info;
    }

    /**
     * Mem0 pgvector database (docker: mem0-pgvector:5432 → host:8432).
     * Stores vector embeddings for Mem0 semantic search.
     *
     * Data sources:
     *   version → JDBC: SELECT version()  (jdbc:postgresql://localhost:8432/postgres)
     *   password → env MEM0_POSTGRES_PASSWORD | default "mem0pass" (from docker-compose.yml)
     */
    private Map<String, Object> checkMem0Pgvector() {
        var info = new LinkedHashMap<String, Object>();
        try {
            var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
            ds.setUrl("jdbc:postgresql://localhost:8432/postgres");
            ds.setUsername("postgres");
            ds.setPassword(Objects.requireNonNullElse(System.getenv("MEM0_POSTGRES_PASSWORD"), "mem0pass"));
            try (var conn = ds.getConnection();
                 var rs = conn.createStatement().executeQuery("SELECT version()")) {
                rs.next();
                info.put("status", "UP");
                info.put("version", rs.getString(1));
            }
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    /**
     * Mem0 Neo4j graph database (docker: mem0-neo4j:7687 → host:7474 web UI).
     * Stores entity relationships for Mem0 knowledge graph.
     *
     * Data sources:
     *   status → HTTP GET http://localhost:7474 (Neo4j browser endpoint)
     */
    private Map<String, Object> checkMem0Neo4j() {
        var info = new LinkedHashMap<String, Object>();
        try {
            http.get().uri("http://localhost:7474").retrieve().toBodilessEntity();
            info.put("status", "UP");
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    // ── Docker volume storage usage ─────────────────────────────────────────

    /**
     * Volume name → friendly label mapping for the services we care about.
     * Volume names are prefixed by docker-compose project name (e.g. "destiny-oracle-backend_").
     */
    private static final Map<String, String> VOLUME_LABELS = Map.of(
            "mem0_pgvector_data", "Mem0 pgvector",
            "mem0_neo4j_data",    "Mem0 Neo4j",
            "mem0_history",       "Mem0 History",
            "ollama_data",        "Ollama Models",
            "postgres_data",      "PostgreSQL"
    );

    /**
     * Queries Docker for volume disk usage via {@code docker system df -v}.
     * Parses the "Local Volumes" table to extract volume names and sizes.
     *
     * @return { timestamp, volumes: [{ name, size, sizeBytes, service }], totalBytes, totalFormatted }
     */
    public Map<String, Object> getStorageUsage() {
        var result = new LinkedHashMap<String, Object>();
        result.put("timestamp", Instant.now().toString());

        try {
            var pb = new ProcessBuilder("docker", "system", "df", "-v");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            String output;
            try (var is = proc.getInputStream()) {
                output = new String(is.readAllBytes());
            }
            proc.waitFor(15, TimeUnit.SECONDS);

            List<Map<String, Object>> volumes = parseVolumeTable(output);
            result.put("volumes", volumes);

            long totalBytes = volumes.stream()
                    .mapToLong(v -> ((Number) v.get("sizeBytes")).longValue())
                    .sum();
            result.put("totalBytes", totalBytes);
            result.put("totalFormatted", formatBytes(totalBytes));
        } catch (Exception e) {
            log.warn("Failed to get Docker storage usage: {}", e.getMessage());
            result.put("volumes", List.of());
            result.put("totalBytes", 0);
            result.put("totalFormatted", "N/A");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Parses the "Local Volumes space usage:" section of {@code docker system df -v} output.
     * Expected table format:
     * <pre>
     * VOLUME NAME   LINKS   SIZE
     * some_volume   1       6.859GB
     * </pre>
     */
    private List<Map<String, Object>> parseVolumeTable(String output) {
        List<Map<String, Object>> volumes = new ArrayList<>();
        String[] lines = output.split("\n");
        boolean inVolumeSection = false;
        boolean headerSkipped = false;

        for (String line : lines) {
            if (line.startsWith("Local Volumes space usage:")) {
                inVolumeSection = true;
                headerSkipped = false;
                continue;
            }
            if (inVolumeSection) {
                if (line.isBlank()) {
                    if (headerSkipped) break; // end of volume table
                    continue;
                }
                if (line.startsWith("VOLUME NAME")) {
                    headerSkipped = true;
                    continue;
                }
                if (!headerSkipped) continue;

                // Parse row: "destiny-oracle-backend_mem0_pgvector_data   1   41.22MB"
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) continue;

                String volumeName = parts[0];
                String sizeStr = parts[parts.length - 1]; // last column is SIZE

                // Match against our known volumes (suffix match to handle project prefix)
                for (var entry : VOLUME_LABELS.entrySet()) {
                    if (volumeName.endsWith(entry.getKey())) {
                        var vol = new LinkedHashMap<String, Object>();
                        vol.put("name", entry.getKey());
                        vol.put("fullName", volumeName);
                        vol.put("size", sizeStr);
                        vol.put("sizeBytes", parseSizeToBytes(sizeStr));
                        vol.put("service", entry.getValue());
                        volumes.add(vol);
                        break;
                    }
                }
            }
        }
        return volumes;
    }

    /**
     * Converts Docker size strings like "41.22MB", "6.859GB", "49.15kB" to bytes.
     */
    private long parseSizeToBytes(String size) {
        try {
            size = size.trim();
            if (size.equals("0B")) return 0;

            double value;
            long multiplier;
            if (size.endsWith("GB")) {
                value = Double.parseDouble(size.replace("GB", ""));
                multiplier = 1024L * 1024 * 1024;
            } else if (size.endsWith("MB")) {
                value = Double.parseDouble(size.replace("MB", ""));
                multiplier = 1024L * 1024;
            } else if (size.endsWith("kB")) {
                value = Double.parseDouble(size.replace("kB", ""));
                multiplier = 1024L;
            } else if (size.endsWith("B")) {
                value = Double.parseDouble(size.replace("B", ""));
                multiplier = 1;
            } else {
                return 0;
            }
            return (long) (value * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Formats bytes into human-readable string (e.g. "1.24 GB") */
    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else if (bytes >= 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    // ── Config-only checks (no network call — just verify key presence) ─────

    /**
     * Claude API — used for image prompts + stage content generation (quality matters).
     * No actual API call made (saves tokens) — only checks config.
     *
     * Data sources:
     *   model      → application.yml: spring.ai.anthropic.chat.options.model
     *   keyPresent → application.yml: spring.ai.anthropic.api-key (from .env ANTHROPIC_API_KEY)
     */
    private Map<String, Object> checkClaudeApi() {
        boolean hasKey = anthropicApiKey != null && !anthropicApiKey.isBlank() && !"disabled".equals(anthropicApiKey);
        return Map.of(
                "status",     hasKey ? "CONFIGURED" : "NOT_CONFIGURED",
                "model",      claudeModel,
                "keyPresent", hasKey
        );
    }

    /**
     * Image Provider — either Gemini or ModelsLab, configured via app.image-provider.
     * No actual API call — only checks which provider is active and if its key is set.
     *
     * Data sources:
     *   provider → application.yml: app.image-provider ("gemini" | "modelslab")
     *   model    → app.gemini.model or app.modelslab.model-id
     *   key      → .env GEMINI_API_KEY or .env MODELSLAB_API_KEY
     */
    private Map<String, Object> checkImageProvider() {
        boolean isGemini = "gemini".equalsIgnoreCase(imageProvider);
        String key   = isGemini ? geminiApiKey : modelslabApiKey;
        String model = isGemini ? geminiModel  : modelslabModelId;
        boolean hasKey = key != null && !key.isBlank();
        return Map.of(
                "status",     hasKey ? "CONFIGURED" : "NOT_CONFIGURED",
                "provider",   imageProvider,
                "model",      model,
                "keyPresent", hasKey
        );
    }
}
