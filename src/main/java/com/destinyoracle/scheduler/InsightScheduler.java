package com.destinyoracle.scheduler;

import com.destinyoracle.domain.chat.entity.DailyInsight;
import com.destinyoracle.domain.chat.repository.DailyInsightRepository;
import com.destinyoracle.domain.notification.repository.ReminderRepository;
import com.destinyoracle.domain.task.repository.TaskRepository;
import com.destinyoracle.domain.user.entity.AppUser;
import com.destinyoracle.domain.user.repository.UserRepository;
import com.destinyoracle.domain.notification.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Generates daily insights at 11 PM and sends morning push at 8 AM.
 */
@Component
public class InsightScheduler {

    private static final Logger log = LoggerFactory.getLogger(InsightScheduler.class);

    private final UserRepository userRepo;
    private final TaskRepository taskRepo;
    private final ReminderRepository reminderRepo;
    private final DailyInsightRepository insightRepo;
    private final PushNotificationService pushService;
    private final ChatClient chatClient;

    public InsightScheduler(
        UserRepository userRepo,
        TaskRepository taskRepo,
        ReminderRepository reminderRepo,
        DailyInsightRepository insightRepo,
        PushNotificationService pushService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.userRepo = userRepo;
        this.taskRepo = taskRepo;
        this.reminderRepo = reminderRepo;
        this.insightRepo = insightRepo;
        this.pushService = pushService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Generate daily insights at 11 PM.
     */
    @Scheduled(cron = "0 0 23 * * *")
    @Transactional
    public void generateDailyInsights() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();

        List<AppUser> users = userRepo.findAll();

        for (AppUser user : users) {
            try {
                // Skip if already generated
                if (insightRepo.findByUserIdAndInsightDate(user.getId(), today).isPresent()) continue;

                int tasksCompleted = taskRepo.countCompletedSince(user.getId(), startOfDay);
                int remindersMissed = reminderRepo.countActiveUnseen(user.getId());

                String prompt = String.format("""
                    Generate a brief, encouraging daily summary for a personal growth app user.
                    Stats today:
                    - Tasks completed: %d
                    - Reminders pending: %d

                    Write 2-3 short sentences summarizing their day and one suggestion for tomorrow.
                    Be warm and motivating. Keep it under 100 words.
                    """, tasksCompleted, remindersMissed);

                String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

                DailyInsight insight = DailyInsight.builder()
                    .userId(user.getId())
                    .insightDate(today)
                    .content(content)
                    .tasksCompleted(tasksCompleted)
                    .build();

                insightRepo.save(insight);
                log.debug("Daily insight generated for user {}", user.getId());
            } catch (Exception e) {
                log.error("Failed to generate insight for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    /**
     * Send morning push with yesterday's insight at 8 AM.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendMorningInsight() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        List<AppUser> users = userRepo.findAll();

        for (AppUser user : users) {
            insightRepo.findByUserIdAndInsightDate(user.getId(), yesterday)
                .ifPresent(insight -> {
                    String summary = insight.getContent();
                    if (summary.length() > 150) summary = summary.substring(0, 147) + "...";
                    pushService.sendToUser(user.getId(), "Your Daily Recap ✨", summary);
                });
        }
    }
}
