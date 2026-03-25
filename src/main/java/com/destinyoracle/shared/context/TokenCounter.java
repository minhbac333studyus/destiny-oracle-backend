package com.destinyoracle.shared.context;

import org.springframework.stereotype.Component;

/**
 * Simple token estimator. Uses the ~4 chars/token heuristic for English text.
 * Good enough for budget enforcement — exact counts would require tiktoken.
 */
@Component
public class TokenCounter {

    private static final double CHARS_PER_TOKEN = 4.0;

    /** Alias for estimate — used by tests. */
    public int count(String text) {
        return estimate(text);
    }

    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /** Alias for truncateToFit — used by tests. */
    public String truncate(String text, int maxTokens) {
        return truncateToFit(text, maxTokens);
    }

    /**
     * Truncate text to fit within a token budget.
     * Preserves whole words when possible.
     */
    public String truncateToFit(String text, int maxTokens) {
        if (text == null || text.isEmpty()) return "";
        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        if (text.length() <= maxChars) return text;

        // Try to break at last space before limit
        String truncated = text.substring(0, maxChars);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > maxChars * 0.8) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + "...";
    }
}
