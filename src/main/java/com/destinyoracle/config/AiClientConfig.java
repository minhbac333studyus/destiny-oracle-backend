package com.destinyoracle.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Two ChatClient beans:
 *
 *   "ollamaChatClient" (PRIMARY) — Qwen via Ollama, $0 cost.
 *       → Conversation Compressor, Insight Scheduler
 *
 *   "anthropicChatClient" — Claude Haiku, fast + high quality.
 *       → AI Chat (AiChatServiceImpl), Image Prompt generation, Stage Content generation
 */
@Configuration
public class AiClientConfig {

    /**
     * Primary = default. Any class injecting plain ChatClient.Builder gets Ollama.
     * Used by: AiChatServiceImpl, ConversationCompressor, InsightScheduler
     */
    @Bean
    @Primary
    @Qualifier("ollamaChatClient")
    public ChatClient.Builder ollamaChatClientBuilder(OllamaChatModel ollamaModel) {
        return ChatClient.builder(ollamaModel);
    }

    /**
     * Secondary. Must be injected with @Qualifier("anthropicChatClient").
     * Used by: ImagePromptServiceImpl
     */
    @Bean
    @Qualifier("anthropicChatClient")
    public ChatClient.Builder anthropicChatClientBuilder(AnthropicChatModel anthropicModel) {
        return ChatClient.builder(anthropicModel);
    }
}
