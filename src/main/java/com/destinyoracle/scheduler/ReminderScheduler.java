package com.destinyoracle.scheduler;

import com.destinyoracle.domain.notification.entity.Reminder;
import com.destinyoracle.domain.notification.repository.ReminderRepository;
import com.destinyoracle.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Checks for due reminders every minute and sends push notifications.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderRepository reminderRepo;
    private final PushNotificationService pushService;

    public ReminderScheduler(ReminderRepository reminderRepo, PushNotificationService pushService) {
        this.reminderRepo = reminderRepo;
        this.pushService = pushService;
    }

    @Scheduled(fixedRate = 60_000)  // Every 1 minute
    @Transactional
    public void checkDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> dueReminders = reminderRepo.findDueReminders(now);

        for (Reminder reminder : dueReminders) {
            try {
                pushService.sendToUser(
                    reminder.getUserId(),
                    reminder.getTitle(),
                    reminder.getBody() != null ? reminder.getBody() : ""
                );

                reminder.setNotificationSent(true);
                reminderRepo.save(reminder);

                log.debug("Reminder sent: {} → user {}", reminder.getTitle(), reminder.getUserId());
            } catch (Exception e) {
                log.error("Failed to send reminder {}: {}", reminder.getId(), e.getMessage());
            }
        }

        if (!dueReminders.isEmpty()) {
            log.info("Processed {} due reminders", dueReminders.size());
        }
    }
}
