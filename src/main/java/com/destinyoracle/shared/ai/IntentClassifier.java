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
        TASK,        // "create a workout plan", "give me exercises"
        REMINDER,    // "remind me to...", "set an alarm for..."
        PLAN_SAVE,   // "save this plan", "save as my leg day"
        PLAN_QUERY,  // "what's my leg day?", "show my workout"
        DAILY_PLAN,  // "plan for today", "generate daily plan"
        INSIGHT,     // "how was my day?", "daily summary"
        GENERAL      // Everything else
    }

    private static final Pattern REMINDER_PATTERN = Pattern.compile(
        "(?i)(remind|reminder|alarm|notify|notification|alert|set.*reminder|set.*reminders)\\b.*\\b(me|at|in|every|tomorrow|today|AM|PM|\\d{1,2}:\\d{2})"
    );

    private static final Pattern TASK_PATTERN = Pattern.compile(
        "(?i)(create|make|give|build|generate|design|plan|help me plan|prep|checklist|organize|track)\\b.*\\b(plan|workout|routine|exercise|meal|task|list|schedule|for today|for this week|today's|meals|supplements|steps)"
    );

    private static final Pattern PLAN_SAVE_PATTERN = Pattern.compile(
        "(?i)(save|store|keep|remember)\\b.*\\b(plan|this|it|as my)"
    );

    private static final Pattern PLAN_QUERY_PATTERN = Pattern.compile(
        "(?i)(what('s| is| are)|show|get|my)\\b.*\\b(plan|workout|routine|leg day|arm day|meal|schedule)"
    );

    private static final Pattern DAILY_PLAN_PATTERN = Pattern.compile(
        "(?i)(plan|l\u1ecbch|k\u1ebf ho\u1ea1ch|l\u00ean plan|generate|t\u1ea1o|sinh)\\b.*\\b(h\u00f4m nay|today|ng\u00e0y mai|tomorrow|daily|h\u00e0ng ng\u00e0y|backward)"
    );

    private static final Pattern INSIGHT_PATTERN = Pattern.compile(
        "(?i)(how was|summary|daily|today's|yesterday's)\\b.*\\b(day|summary|insight|review|progress)"
    );

    /** Quick keyword check for Blueprint-style prompts that are too long for regex. */
    private static boolean containsActionKeywords(String msg) {
        String lower = msg.toLowerCase();
        return (lower.contains("plan") && (lower.contains("meal") || lower.contains("workout") || lower.contains("exercise")))
            || (lower.contains("reminder") && (lower.contains("set") || lower.contains("create")))
            || lower.contains("give me") && (lower.contains("plan") || lower.contains("routine"))
            || lower.contains("help me plan")
            || lower.contains("for today") && (lower.contains("plan") || lower.contains("meal") || lower.contains("workout"));
    }

    public Intent classify(String message) {
        if (message == null || message.isBlank()) return Intent.GENERAL;

        if (REMINDER_PATTERN.matcher(message).find()) return Intent.REMINDER;
        if (DAILY_PLAN_PATTERN.matcher(message).find()) return Intent.DAILY_PLAN;
        if (PLAN_SAVE_PATTERN.matcher(message).find()) return Intent.PLAN_SAVE;
        if (PLAN_QUERY_PATTERN.matcher(message).find()) return Intent.PLAN_QUERY;
        if (TASK_PATTERN.matcher(message).find()) return Intent.TASK;
        if (INSIGHT_PATTERN.matcher(message).find()) return Intent.INSIGHT;

        // Fallback: catch long prompts (e.g. Blueprint) with action-oriented keywords
        if (containsActionKeywords(message)) return Intent.TASK;

        return Intent.GENERAL;
    }
}
