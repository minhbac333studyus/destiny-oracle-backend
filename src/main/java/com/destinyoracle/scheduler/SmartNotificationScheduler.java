package com.destinyoracle.scheduler;

import com.destinyoracle.domain.notification.entity.ActivityLog;
import com.destinyoracle.domain.notification.entity.NotificationRule;
import com.destinyoracle.domain.notification.entity.NotificationRule.ScheduleType;
import com.destinyoracle.domain.notification.entity.Reminder;
import com.destinyoracle.domain.notification.repository.ActivityLogRepository;
import com.destinyoracle.domain.notification.repository.NotificationRuleRepository;
import com.destinyoracle.domain.notification.repository.ReminderRepository;
import com.destinyoracle.domain.task.entity.Task;
import com.destinyoracle.domain.task.repository.TaskRepository;
import com.destinyoracle.domain.task.repository.TaskStepRepository;
import com.destinyoracle.domain.notification.service.PushNotificationService;
import com.destinyoracle.domain.task.service.TaskService;
import com.destinyoracle.domain.task.service.TaskService.StepInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluates user notification rules every 5 minutes.
 *
 * <h3>Rule evaluation flow:</h3>
 * <pre>
 *   For each user with active rules:
 *     1. Reset firedToday if date changed
 *     2. For each rule, check shouldFire():
 *        a. Quiet hours? → skip
 *        b. Daily quota reached? → skip
 *        c. Cooldown active? → skip
 *        d. Suppress during workout? → skip
 *        e. Schedule type match? (INTERVAL / DAILY / WEEKLY / WATER)
 *     3. REMINDER rules → create Reminder directly
 *     4. TASK rules → call AI for structured steps, then create Task
 *     5. Update lastFiredAt, increment firedToday
 * </pre>
 *
 * <h3>WATER schedule type:</h3>
 * Auto-calculates 3 reminder times from bedtime/wakeTime:
 *   - Reminder 1: wakeTime + 30 min
 *   - Reminder 2: midpoint(wake, lastWater)
 *   - Reminder 3: bedtime - 3 hours (last call)
 */
@Component
public class SmartNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SmartNotificationScheduler.class);

    private final NotificationRuleRepository ruleRepo;
    private final ReminderRepository reminderRepo;
    private final ActivityLogRepository activityRepo;
    private final TaskRepository taskRepo;
    private final TaskService taskService;
    private final PushNotificationService pushService;
    private final ChatClient chatClient;

    public SmartNotificationScheduler(
        NotificationRuleRepository ruleRepo,
        ReminderRepository reminderRepo,
        ActivityLogRepository activityRepo,
        TaskRepository taskRepo,
        TaskService taskService,
        PushNotificationService pushService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.ruleRepo = ruleRepo;
        this.reminderRepo = reminderRepo;
        this.activityRepo = activityRepo;
        this.taskRepo = taskRepo;
        this.taskService = taskService;
        this.pushService = pushService;
        this.chatClient = chatClientBuilder.build();
    }

    @Scheduled(fixedRate = 300_000)  // Every 5 minutes
    @Transactional
    public void evaluate() {
        log.debug("SmartNotificationScheduler: evaluating rules...");

        // Group rules by user
        List<NotificationRule> allActive = ruleRepo.findAll().stream()
            .filter(NotificationRule::getActive)
            .toList();

        Map<UUID, List<NotificationRule>> byUser = allActive.stream()
            .collect(Collectors.groupingBy(NotificationRule::getUserId));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();

        for (var entry : byUser.entrySet()) {
            UUID userId = entry.getKey();
            List<NotificationRule> rules = entry.getValue();

            // Reset daily counters if date changed
            for (NotificationRule rule : rules) {
                if (rule.getFiredTodayDate() == null || !rule.getFiredTodayDate().equals(today)) {
                    rule.setFiredToday(0);
                    rule.setFiredTodayDate(today);
                }
            }

            // Check workout suppression: is user actively working out?
            boolean workoutActive = isWorkoutActive(userId, now);

            for (NotificationRule rule : rules) {
                try {
                    if (shouldFire(rule, now, nowTime, today, workoutActive, userId)) {
                        fireRule(rule, userId, now);
                    }
                } catch (Exception e) {
                    log.error("Failed to fire rule {} for user {}: {}",
                        rule.getId(), userId, e.getMessage());
                }
            }

            ruleRepo.saveAll(rules);
        }
    }

    // ── Rule evaluation ──────────────────────────────────────────────────

    private boolean shouldFire(NotificationRule rule, LocalDateTime now, LocalTime nowTime,
                               LocalDate today, boolean workoutActive, UUID userId) {

        // Quiet hours
        if (rule.getQuietStart() != null && rule.getQuietEnd() != null) {
            if (isInQuietHours(nowTime, rule.getQuietStart(), rule.getQuietEnd())) {
                return false;
            }
        }

        // Daily quota
        if (rule.getDailyQuota() != null && rule.getFiredToday() >= rule.getDailyQuota()) {
            return false;
        }

        // Cooldown
        if (rule.getCooldownMinutes() != null && rule.getLastFiredAt() != null) {
            if (now.isBefore(rule.getLastFiredAt().plusMinutes(rule.getCooldownMinutes()))) {
                return false;
            }
        }

        // Suppress during workout
        if (rule.getSuppressDuringWorkout() && workoutActive) {
            return false;
        }

        // Schedule-specific checks
        return switch (rule.getSchedule()) {
            case INTERVAL -> shouldFireInterval(rule, now);
            case DAILY -> shouldFireDaily(rule, nowTime, today);
            case WEEKLY -> shouldFireWeekly(rule, nowTime, today);
            case WATER -> shouldFireWater(rule, nowTime, userId);
            case CUSTOM -> false; // TODO: cron parsing
        };
    }

    private boolean shouldFireInterval(NotificationRule rule, LocalDateTime now) {
        if (rule.getIntervalMinutes() == null) return false;
        if (rule.getLastFiredAt() == null) return true;
        return now.isAfter(rule.getLastFiredAt().plusMinutes(rule.getIntervalMinutes()));
    }

    private boolean shouldFireDaily(NotificationRule rule, LocalTime nowTime, LocalDate today) {
        if (rule.getTimeOfDay() == null) return false;
        // Fire if we're within 5 min of the target time and haven't fired today
        return rule.getFiredToday() == 0
            && Math.abs(Duration.between(nowTime, rule.getTimeOfDay()).toMinutes()) <= 5;
    }

    private boolean shouldFireWeekly(NotificationRule rule, LocalTime nowTime, LocalDate today) {
        if (rule.getDayOfWeek() == null || rule.getTimeOfDay() == null) return false;
        return today.getDayOfWeek() == rule.getDayOfWeek()
            && rule.getFiredToday() == 0
            && Math.abs(Duration.between(nowTime, rule.getTimeOfDay()).toMinutes()) <= 5;
    }

    /**
     * WATER schedule: auto-calculates 3 reminder windows from sleep schedule.
     *
     * Window 1: wakeTime + 30 min (morning hydration)
     * Window 2: midpoint between wake and last-water-time
     * Window 3: bedtime - 3h (last call — stop here to avoid nocturia)
     *
     * Each window has a ±5 min tolerance. firedToday tracks which have fired.
     */
    private boolean shouldFireWater(NotificationRule rule, LocalTime nowTime, UUID userId) {
        if (rule.getBedtime() == null || rule.getWakeTime() == null) return false;
        if (rule.getDailyQuota() != null && rule.getFiredToday() >= rule.getDailyQuota()) return false;

        LocalTime wake = rule.getWakeTime();
        LocalTime lastWater = rule.getBedtime().minusHours(3);

        // Calculate the 3 windows
        LocalTime window1 = wake.plusMinutes(30);
        long totalMinutes = Duration.between(window1, lastWater).toMinutes();
        LocalTime window2 = window1.plusMinutes(totalMinutes / 2);
        LocalTime window3 = lastWater;

        LocalTime[] windows = { window1, window2, window3 };
        int targetWindow = rule.getFiredToday(); // 0, 1, or 2

        if (targetWindow >= windows.length) return false;

        LocalTime target = windows[targetWindow];
        long diff = Duration.between(target, nowTime).toMinutes();
        return diff >= 0 && diff <= 5;
    }

    // ── Fire a rule ──────────────────────────────────────────────────────

    private void fireRule(NotificationRule rule, UUID userId, LocalDateTime now) {
        if (rule.getOutputType() == NotificationRule.OutputType.REMINDER) {
            fireAsReminder(rule, userId, now);
        } else {
            fireAsTask(rule, userId, now);
        }

        rule.setLastFiredAt(now);
        rule.setFiredToday(rule.getFiredToday() + 1);

        log.info("Fired rule '{}' for user {} (fired {}/{})",
            rule.getName(), userId, rule.getFiredToday(),
            rule.getDailyQuota() != null ? rule.getDailyQuota() : "∞");
    }

    private void fireAsReminder(NotificationRule rule, UUID userId, LocalDateTime now) {
        Reminder reminder = Reminder.builder()
            .userId(userId)
            .title(rule.getName())
            .body(rule.getDescription())
            .scheduledAt(now)
            .repeatType(Reminder.RepeatType.NONE) // scheduler handles repeats, not the reminder
            .build();
        reminderRepo.save(reminder);

        // Push notification
        pushService.sendToUser(userId, rule.getName(),
            rule.getDescription() != null ? rule.getDescription() : "Tap to complete");
    }

    private void fireAsTask(NotificationRule rule, UUID userId, LocalDateTime now) {
        // Check if there's already an active task of the same category today
        List<Task> activeTasks = taskRepo.findByUserIdAndStatus(userId, Task.TaskStatus.ACTIVE);
        boolean hasSameCategory = activeTasks.stream()
            .anyMatch(t -> t.getCategory() == rule.getCategory()
                && t.getCreatedAt() != null
                && t.getCreatedAt().toLocalDate().equals(now.toLocalDate()));
        if (hasSameCategory) {
            log.debug("Skipping task creation for rule '{}' — active {} task already exists today",
                rule.getName(), rule.getCategory());
            return;
        }

        // For TASK-type rules, reuse cached AI steps or generate fresh (saves tokens on recurring rules)
        try {
            List<StepInput> steps;
            if (rule.getCachedSteps() != null && !rule.getCachedSteps().isBlank()) {
                steps = parseTaskSteps(rule.getCachedSteps());
                log.debug("Reusing cached steps for rule '{}' (saved AI call)", rule.getName());
            } else {
                String prompt = buildTaskPrompt(rule);
                String aiResponse = chatClient.prompt().user(prompt).call().content();
                steps = parseTaskSteps(aiResponse);
                // Cache for future fires
                if (!steps.isEmpty()) {
                    rule.setCachedSteps(aiResponse);
                }
            }

            if (steps.isEmpty()) {
                steps = List.of(new StepInput(rule.getName(), rule.getDescription(), null));
            }

            taskService.createTask(userId, rule.getName(), rule.getCategory(), steps);

            pushService.sendToUser(userId, "New task: " + rule.getName(),
                steps.size() + " steps ready");
        } catch (Exception e) {
            log.error("AI task generation failed for rule '{}': {}", rule.getName(), e.getMessage());
            // Fallback: create simple single-step task
            taskService.createTask(userId, rule.getName(), rule.getCategory(),
                List.of(new StepInput(rule.getName(), rule.getDescription(), null)));
        }
    }

    // ── AI Prompt ────────────────────────────────────────────────────────

    private String buildTaskPrompt(NotificationRule rule) {
        return String.format("""
            Generate a concise task with 3-6 actionable steps as a JSON array.

            Task: %s
            Category: %s
            Context: %s

            Return ONLY a JSON array of objects with "title" and "description" fields.
            Example: [{"title":"Warm up","description":"5 min light cardio"},{"title":"Main set","description":"3x12 bench press"}]

            Keep steps short and practical. No markdown, no explanation — only the JSON array.
            """,
            rule.getName(),
            rule.getCategory(),
            rule.getDescription() != null ? rule.getDescription() : "No additional context"
        );
    }

    @SuppressWarnings("unchecked")
    private List<StepInput> parseTaskSteps(String aiResponse) {
        try {
            // Extract JSON array from response (AI might wrap it in markdown)
            String json = aiResponse.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end < 0) return List.of();
            json = json.substring(start, end + 1);

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, String>> items = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            return items.stream()
                .map(m -> new StepInput(
                    m.getOrDefault("title", "Step"),
                    m.get("description"),
                    null))
                .toList();
        } catch (Exception e) {
            log.warn("Failed to parse AI task steps: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Check if user has an active WORKOUT task with steps completed in last 60 min */
    private boolean isWorkoutActive(UUID userId, LocalDateTime now) {
        List<Task> workoutTasks = taskRepo.findByUserIdAndStatus(userId, Task.TaskStatus.ACTIVE)
            .stream()
            .filter(t -> t.getCategory() == Task.TaskCategory.WORKOUT)
            .toList();

        if (workoutTasks.isEmpty()) return false;

        // Check if any workout step was completed in the last 60 minutes
        LocalDateTime cutoff = now.minusMinutes(60);
        for (Task task : workoutTasks) {
            boolean recentActivity = task.getSteps().stream()
                .anyMatch(s -> s.getCompleted() && s.getCompletedAt() != null
                    && s.getCompletedAt().isAfter(cutoff));
            if (recentActivity) return true;
        }
        return false;
    }

    /** Check if current time falls within quiet hours (handles midnight crossing) */
    private boolean isInQuietHours(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            // Same day: e.g. 22:00 - 23:00 (unlikely but valid)
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // Crosses midnight: e.g. 22:00 - 07:00
            return !now.isBefore(start) || now.isBefore(end);
        }
    }
}
