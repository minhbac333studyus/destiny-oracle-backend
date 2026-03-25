package com.destinyoracle.shared.ai;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Classifies user message intent to determine if AI should
 * create a task, reminder, saved plan, or just chat.
 */
@Component
public class IntentClassifier {

    public enum Intent {
        TASK,       // "create a workout plan", "give me exercises"
        REMINDER,   // "remind me to...", "set an alarm for..."
        PLAN_SAVE,  // "save this plan", "save as my leg day"
        PLAN_QUERY, // "what's my leg day?", "show my workout"
        INSIGHT,    // "how was my day?", "daily summary"
        GENERAL     // Everything else
    }

    private static final Pattern REMINDER_PATTERN = Pattern.compile(
        "(?i)(remind|reminder|alarm|notify|notification|alert)\\b.*\\b(me|at|in|every|tomorrow|today)"
    );

    private static final Pattern TASK_PATTERN = Pattern.compile(
        "(?i)(create|make|give|build|generate|design)\\b.*\\b(plan|workout|routine|exercise|meal|task|list|schedule)"
    );

    private static final Pattern PLAN_SAVE_PATTERN = Pattern.compile(
        "(?i)(save|store|keep|remember)\\b.*\\b(plan|this|it|as my)"
    );

    private static final Pattern PLAN_QUERY_PATTERN = Pattern.compile(
        "(?i)(what('s| is| are)|show|get|my)\\b.*\\b(plan|workout|routine|leg day|arm day|meal|schedule)"
    );

    private static final Pattern INSIGHT_PATTERN = Pattern.compile(
        "(?i)(how was|summary|daily|today's|yesterday's)\\b.*\\b(day|summary|insight|review|progress)"
    );

    public Intent classify(String message) {
        if (message == null || message.isBlank()) return Intent.GENERAL;

        if (REMINDER_PATTERN.matcher(message).find()) return Intent.REMINDER;
        if (PLAN_SAVE_PATTERN.matcher(message).find()) return Intent.PLAN_SAVE;
        if (PLAN_QUERY_PATTERN.matcher(message).find()) return Intent.PLAN_QUERY;
        if (TASK_PATTERN.matcher(message).find()) return Intent.TASK;
        if (INSIGHT_PATTERN.matcher(message).find()) return Intent.INSIGHT;

        return Intent.GENERAL;
    }
}
