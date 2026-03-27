package com.destinyoracle.service;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

@Service
public class SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    private final DataSource dataSource;
    private final RestClient restClient;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${mem0.base-url:http://localhost:8888}")
    private String mem0BaseUrl;

    @Value("${spring.ai.anthropic.api-key:disabled}")
    private String anthropicApiKey;

    @Value("${spring.ai.anthropic.chat.options.model:claude-haiku-4-5-20251001}")
    private String claudeModel;

    @Value("${spring.ai.ollama.chat.model:qwen3:8b}")
    private String ollamaChatModel;

    @Value("${app.image-provider:modelslab}")
    private String imageProvider;

    @Value("${app.gemini.api-key:#{null}}")
    private String geminiApiKey;

    @Value("${app.gemini.model:gemini-2.5-flash-image}")
    private String geminiModel;

    @Value("${app.modelslab.api-key:#{null}}")
    private String modelslabApiKey;

    @Value("${app.modelslab.model-id:qwen}")
    private String modelslabModelId;

    public SystemHealthService(DataSource dataSource, RestClient.Builder restClientBuilder) {
        this.dataSource = dataSource;
        this.restClient = restClientBuilder.build();
    }

    public Map<String, Object> getFullHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("oracleBackend", checkOracleBackend());
        services.put("postgres", checkPostgres());
        services.put("ollama", checkOllama());
        services.put("mem0Api", checkMem0Api());
        services.put("mem0Pgvector", checkMem0Pgvector());
        services.put("mem0Neo4j", checkMem0Neo4j());
        services.put("claudeApi", checkClaudeApi());
        services.put("imageProvider", checkImageProvider());

        result.put("services", services);
        return result;
    }

    private Map<String, Object> checkOracleBackend() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        info.put("jvmMemoryUsedMb", usedMb);
        info.put("jvmMemoryMaxMb", maxMb);
        info.put("activeThreads", Thread.activeCount());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return info;
    }

    private Map<String, Object> checkPostgres() {
        Map<String, Object> info = new LinkedHashMap<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT version()")) {
            rs.next();
            info.put("status", "UP");
            info.put("version", rs.getString(1));

            if (dataSource instanceof HikariDataSource hds) {
                var pool = hds.getHikariPoolMXBean();
                if (pool != null) {
                    info.put("activeConnections", pool.getActiveConnections());
                    info.put("idleConnections", pool.getIdleConnections());
                    info.put("totalConnections", pool.getTotalConnections());
                    info.put("maxConnections", hds.getMaximumPoolSize());
                }
            }
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkOllama() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("baseUrl", ollamaBaseUrl);
        info.put("chatModel", ollamaChatModel);
        try {
            var response = restClient.get()
                    .uri(ollamaBaseUrl + "/api/tags")
                    .retrieve()
                    .body(Map.class);

            info.put("status", "UP");
            if (response != null && response.containsKey("models")) {
                List<Map<String, Object>> models = (List<Map<String, Object>>) response.get("models");
                info.put("models", models.stream()
                        .map(m -> m.get("name"))
                        .filter(Objects::nonNull)
                        .toList());
            }
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    private Map<String, Object> checkMem0Api() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("baseUrl", mem0BaseUrl);
        try {
            long start = System.currentTimeMillis();
            restClient.get()
                    .uri(mem0BaseUrl + "/docs")
                    .retrieve()
                    .toBodilessEntity();
            long elapsed = System.currentTimeMillis() - start;

            info.put("status", "UP");
            info.put("responseTimeMs", elapsed);
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    private Map<String, Object> checkMem0Pgvector() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            // Connect to mem0's pgvector on port 8432
            var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
            ds.setUrl("jdbc:postgresql://localhost:8432/postgres");
            ds.setUsername("postgres");
            ds.setPassword(System.getenv("MEM0_POSTGRES_PASSWORD") != null
                    ? System.getenv("MEM0_POSTGRES_PASSWORD") : "mem0pass");

            try (var conn = ds.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT version()")) {
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

    private Map<String, Object> checkMem0Neo4j() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            restClient.get()
                    .uri("http://localhost:7474")
                    .retrieve()
                    .toBodilessEntity();
            info.put("status", "UP");
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    private Map<String, Object> checkClaudeApi() {
        Map<String, Object> info = new LinkedHashMap<>();
        boolean keyPresent = anthropicApiKey != null
                && !anthropicApiKey.isBlank()
                && !"disabled".equals(anthropicApiKey);
        info.put("status", keyPresent ? "CONFIGURED" : "NOT_CONFIGURED");
        info.put("model", claudeModel);
        info.put("keyPresent", keyPresent);
        return info;
    }

    private Map<String, Object> checkImageProvider() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("provider", imageProvider);

        if ("gemini".equalsIgnoreCase(imageProvider)) {
            boolean keyPresent = geminiApiKey != null && !geminiApiKey.isBlank();
            info.put("status", keyPresent ? "CONFIGURED" : "NOT_CONFIGURED");
            info.put("model", geminiModel);
            info.put("keyPresent", keyPresent);
        } else {
            boolean keyPresent = modelslabApiKey != null && !modelslabApiKey.isBlank();
            info.put("status", keyPresent ? "CONFIGURED" : "NOT_CONFIGURED");
            info.put("model", modelslabModelId);
            info.put("keyPresent", keyPresent);
        }
        return info;
    }
}
