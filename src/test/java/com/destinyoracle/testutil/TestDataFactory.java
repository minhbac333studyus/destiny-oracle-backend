package com.destinyoracle.testutil;

import com.destinyoracle.domain.chat.entity.*;
import com.destinyoracle.domain.plan.entity.*;
import com.destinyoracle.domain.task.entity.*;
import com.destinyoracle.domain.notification.entity.*;

import java.time.*;
import java.util.*;

public class TestDataFactory {

    public static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID CONV_ID = UUID.randomUUID();
    public static final UUID PLAN_ID = UUID.randomUUID();
    public static final UUID TASK_ID = UUID.randomUUID();

    // ── Conversations ──

    public static AiConversation conversation() {
        return AiConversation.builder()
            .id(CONV_ID)
            .userId(USER_ID)
            .title("Test conversation")
            .build();
    }

    public static AiMessage userMessage(String content) {
        return AiMessage.builder()
            .id(UUID.randomUUID())
            .role("USER")
            .content(content)
            .compressed(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static AiMessage assistantMessage(String content) {
        return AiMessage.builder()
            .id(UUID.randomUUID())
            .role("ASSISTANT")
            .content(content)
            .compressed(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static List<AiMessage> messageHistory(int count) {
        List<AiMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                messages.add(userMessage("User message " + i));
            } else {
                messages.add(assistantMessage("Assistant reply " + i));
            }
        }
        return messages;
    }

    public static ConversationMemory summary(String content, int round) {
        return ConversationMemory.builder()
            .id(UUID.randomUUID())
            .conversationId(CONV_ID)
            .summary(content)
            .messagesCompressed(10)
            .tokenEstimate(content.length() / 4)
            .compressionRound(round)
            .build();
    }

    // ── Plans ──

    public static SavedPlan legDayPlan() {
        return SavedPlan.builder()
            .id(PLAN_ID)
            .userId(USER_ID)
            .name("Leg day")
            .slug("leg-day")
            .type(SavedPlan.PlanType.WORKOUT)
            .description("4 exercises for legs")
            .content("""
                {"exercises":[
                    {"name":"Barbell squat","sets":4,"reps":"8-10","rest":"90s"},
                    {"name":"Romanian deadlift","sets":3,"reps":"10-12","rest":"60s"},
                    {"name":"Leg press","sets":3,"reps":"12-15","rest":"60s"},
                    {"name":"Calf raise","sets":3,"reps":"20","rest":"30s"}
                ]}""")
            .version(1)
            .active(true)
            .build();
    }

    public static PlanSchedule mondayLegDay() {
        return PlanSchedule.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .savedPlan(legDayPlan())
            .dayOfWeek(DayOfWeek.MONDAY)
            .timeOfDay(LocalTime.of(8, 0))
            .repeatType("WEEKLY")
            .notifyBefore(true)
            .notifyMinutesBefore(15)
            .active(true)
            .build();
    }

    public static NudgeState nudgeLevel(int level) {
        return NudgeState.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .planScheduleId(UUID.randomUUID())
            .nudgeDate(LocalDate.now())
            .nudgeLevel(level)
            .completed(false)
            .skipped(false)
            .build();
    }

    // ── Tasks ──

    public static Task activeTask() {
        Task task = Task.builder()
            .id(TASK_ID)
            .userId(USER_ID)
            .name("8-session leg workout")
            .category(Task.TaskCategory.WORKOUT)
            .totalSteps(8)
            .completedSteps(3)
            .xpPerStep(15)
            .status(Task.TaskStatus.ACTIVE)
            .build();

        List<TaskStep> steps = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            steps.add(TaskStep.builder()
                .id(UUID.randomUUID())
                .task(task)
                .stepNumber(i)
                .title("Session " + i)
                .completed(i <= 3)
                .completedAt(i <= 3 ? LocalDateTime.now().minusDays(8 - i) : null)
                .build());
        }
        task.setSteps(steps);
        return task;
    }

    // ── Reminders ──

    public static Reminder dueReminder() {
        return Reminder.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .title("Buy groceries")
            .body("Rice noodles, chicken, bean sprouts")
            .scheduledAt(LocalDateTime.now().minusMinutes(5))
            .repeatType(Reminder.RepeatType.NONE)
            .notificationSent(false)
            .completed(false)
            .build();
    }

    public static Reminder weeklyReminder() {
        return Reminder.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .title("Leg day")
            .scheduledAt(LocalDateTime.now().plusDays(1))
            .repeatType(Reminder.RepeatType.WEEKLY)
            .notificationSent(false)
            .completed(false)
            .build();
    }

    // ── Devices ──

    public static DeviceToken iosDevice() {
        return DeviceToken.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .platform(DeviceToken.Platform.IOS)
            .deviceToken("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6abcd")
            .active(true)
            .build();
    }
}
