package com.destinyoracle.service.impl;

import com.destinyoracle.domain.notification.entity.Reminder;
import com.destinyoracle.domain.notification.repository.ReminderRepository;
import com.destinyoracle.dto.request.CreateReminderRequest;
import com.destinyoracle.dto.response.ReminderResponse;
import com.destinyoracle.service.ReminderService;
import com.destinyoracle.shared.event.ReminderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReminderServiceImpl implements ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);

    private final ReminderRepository reminderRepo;
    private final ApplicationEventPublisher eventPublisher;

    public ReminderServiceImpl(ReminderRepository reminderRepo, ApplicationEventPublisher eventPublisher) {
        this.reminderRepo = reminderRepo;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ReminderResponse createReminder(UUID userId, CreateReminderRequest request) {
        Reminder reminder = Reminder.builder()
            .userId(userId)
            .title(request.title())
            .body(request.body())
            .scheduledAt(request.scheduledAt())
            .repeatType(request.repeatType() != null ? request.repeatType() : Reminder.RepeatType.NONE)
            .repeatCron(request.repeatCron())
            .taskId(request.taskId())
            .taskStepId(request.taskStepId())
            .build();

        return toResponse(reminderRepo.save(reminder));
    }

    @Override
    public List<ReminderResponse> getActiveReminders(UUID userId) {
        return reminderRepo.findByUserIdAndCompletedFalseOrderByScheduledAtAsc(userId)
            .stream().map(this::toResponse).toList();
    }

    @Override
    public List<ReminderResponse> getUpcomingReminders(UUID userId, int hours) {
        LocalDateTime now = LocalDateTime.now();
        return reminderRepo.findUpcoming(userId, now, now.plusHours(hours))
            .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ReminderResponse completeReminder(UUID userId, UUID reminderId) {
        Reminder reminder = findAndVerify(userId, reminderId);
        reminder.setCompleted(true);
        reminderRepo.save(reminder);

        // Handle repeat — create next occurrence
        if (reminder.getRepeatType() != Reminder.RepeatType.NONE) {
            createNextOccurrence(reminder);
        }

        eventPublisher.publishEvent(new ReminderCompletedEvent(
            reminderId, null, userId  // cardId can be linked via task
        ));

        log.info("Reminder {} completed by user {}", reminderId, userId);
        return toResponse(reminder);
    }

    @Override
    @Transactional
    public ReminderResponse snoozeReminder(UUID userId, UUID reminderId, int minutes) {
        Reminder reminder = findAndVerify(userId, reminderId);
        reminder.setSnoozedUntil(LocalDateTime.now().plusMinutes(minutes));
        reminder.setNotificationSent(false);  // Allow re-send after snooze
        return toResponse(reminderRepo.save(reminder));
    }

    @Override
    @Transactional
    public void deleteReminder(UUID userId, UUID reminderId) {
        Reminder reminder = findAndVerify(userId, reminderId);
        reminderRepo.delete(reminder);
    }

    private Reminder findAndVerify(UUID userId, UUID reminderId) {
        Reminder reminder = reminderRepo.findById(reminderId)
            .orElseThrow(() -> new RuntimeException("Reminder not found"));
        if (!reminder.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        return reminder;
    }

    private void createNextOccurrence(Reminder completed) {
        LocalDateTime nextAt = switch (completed.getRepeatType()) {
            case DAILY -> completed.getScheduledAt().plusDays(1);
            case WEEKLY -> completed.getScheduledAt().plusWeeks(1);
            case MONTHLY -> completed.getScheduledAt().plusMonths(1);
            default -> null;
        };

        if (nextAt != null) {
            Reminder next = Reminder.builder()
                .userId(completed.getUserId())
                .title(completed.getTitle())
                .body(completed.getBody())
                .scheduledAt(nextAt)
                .repeatType(completed.getRepeatType())
                .repeatCron(completed.getRepeatCron())
                .taskId(completed.getTaskId())
                .taskStepId(completed.getTaskStepId())
                .build();
            reminderRepo.save(next);
            log.debug("Created next reminder occurrence at {}", nextAt);
        }
    }

    private ReminderResponse toResponse(Reminder r) {
        return new ReminderResponse(
            r.getId(), r.getTitle(), r.getBody(), r.getScheduledAt(),
            r.getRepeatType(), r.getNotificationSent(), r.getCompleted(),
            r.getSnoozedUntil(), r.getTaskId(), r.getTaskStepId(), r.getCreatedAt()
        );
    }
}
