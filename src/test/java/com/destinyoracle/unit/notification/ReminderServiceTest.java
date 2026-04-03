package com.destinyoracle.unit.notification;

import com.destinyoracle.domain.notification.entity.Reminder;
import com.destinyoracle.domain.notification.repository.ReminderRepository;
import com.destinyoracle.dto.request.CreateReminderRequest;
import com.destinyoracle.dto.response.ReminderResponse;
import com.destinyoracle.domain.notification.service.impl.ReminderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static com.destinyoracle.testutil.TestDataFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock private ReminderRepository reminderRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private ReminderServiceImpl reminderService;

    @Test
    void createReminder_validRequest_returnsResponse() {
        var request = new CreateReminderRequest(
            "Buy groceries", "Rice, chicken", LocalDateTime.now().plusHours(2),
            Reminder.RepeatType.NONE, null, null, null
        );

        when(reminderRepo.save(any())).thenAnswer(inv -> {
            Reminder r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReminderResponse response = reminderService.createReminder(USER_ID, request);

        assertThat(response.title()).isEqualTo("Buy groceries");
        assertThat(response.completed()).isFalse();
        verify(reminderRepo).save(any());
    }

    @Test
    void completeReminder_setsCompleted() {
        Reminder reminder = dueReminder();
        when(reminderRepo.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(reminderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReminderResponse response = reminderService.completeReminder(USER_ID, reminder.getId());

        assertThat(response.completed()).isTrue();
    }

    @Test
    void completeReminder_weeklyRepeat_createsNextOccurrence() {
        Reminder reminder = weeklyReminder();
        reminder.setScheduledAt(LocalDateTime.now());  // make it completable
        when(reminderRepo.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(reminderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reminderService.completeReminder(USER_ID, reminder.getId());

        // Should save twice: once for completing, once for next occurrence
        verify(reminderRepo, times(2)).save(any());
    }

    @Test
    void snoozeReminder_setsSnoozeTime() {
        Reminder reminder = dueReminder();
        when(reminderRepo.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(reminderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReminderResponse response = reminderService.snoozeReminder(USER_ID, reminder.getId(), 30);

        assertThat(response.snoozedUntil()).isNotNull();
        assertThat(response.snoozedUntil()).isAfter(LocalDateTime.now().plusMinutes(25));
    }

    @Test
    void completeReminder_wrongUser_throws() {
        Reminder reminder = dueReminder();
        UUID otherUser = UUID.randomUUID();
        when(reminderRepo.findById(reminder.getId())).thenReturn(Optional.of(reminder));

        assertThatThrownBy(() -> reminderService.completeReminder(otherUser, reminder.getId()))
            .hasMessageContaining("Access denied");
    }
}
