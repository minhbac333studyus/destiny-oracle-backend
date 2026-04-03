package com.destinyoracle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runtime AI routing table — holds which provider each service uses.
 * Can be changed at runtime via POST /api/v1/monitor/ai-routing without restart.
 *
 * Providers: "anthropic" (Claude) | "ollama" (local Qwen)
 */
@Component
public class AiRoutingConfig {

    @Value("${spring.ai.anthropic.chat.options.model:claude-haiku-4-5-20251001}")
    private String anthropicModel;

    @Value("${spring.ai.ollama.chat.model:qwen3:1.7b}")
    private String ollamaModel;

    // volatile → thread-safe for concurrent reads/writes
    private volatile String chatProvider       = "anthropic";
    private volatile String insightProvider    = "ollama";
    private volatile String compressionProvider = "ollama";

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getChatProvider()        { return chatProvider; }
    public String getInsightProvider()     { return insightProvider; }
    public String getCompressionProvider() { return compressionProvider; }
    public String getAnthropicModel()      { return anthropicModel; }
    public String getOllamaModel()         { return ollamaModel; }

    // ── Setters (called from MonitorController) ───────────────────────────────

    public void setChatProvider(String provider)        { this.chatProvider = validated(provider); }
    public void setInsightProvider(String provider)     { this.insightProvider = validated(provider); }
    public void setCompressionProvider(String provider) { this.compressionProvider = validated(provider); }

    private String validated(String provider) {
        if (!"anthropic".equals(provider) && !"ollama".equals(provider)) {
            throw new IllegalArgumentException("Provider must be 'anthropic' or 'ollama', got: " + provider);
        }
        return provider;
    }
}
