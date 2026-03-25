package com.destinyoracle.unit.shared;

import com.destinyoracle.shared.context.TokenCounter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TokenCounterTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    void count_emptyString_returnsZero() {
        assertThat(counter.count("")).isZero();
        assertThat(counter.count(null)).isZero();
    }

    @Test
    void count_shortText_returnsCorrectEstimate() {
        // "hello world" = 11 chars → ceil(11/4) = 3 tokens
        assertThat(counter.count("hello world")).isEqualTo(3);
    }

    @Test
    void count_longText_scalesLinearly() {
        String text = "a".repeat(4000); // 4000 chars → 1000 tokens
        assertThat(counter.count(text)).isEqualTo(1000);
    }

    @Test
    void truncate_withinLimit_returnsOriginal() {
        String text = "short text";
        assertThat(counter.truncate(text, 100)).isEqualTo(text);
    }

    @Test
    void truncate_overLimit_cutsWithEllipsis() {
        String text = "a".repeat(1000); // 1000 chars = 250 tokens
        String result = counter.truncate(text, 50); // 50 tokens = 200 chars
        assertThat(result.length()).isLessThanOrEqualTo(203); // 200 + "..."
        assertThat(result).endsWith("...");
    }
}
