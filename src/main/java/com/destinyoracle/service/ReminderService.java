package com.destinyoracle.service;

import com.destinyoracle.dto.request.CreateReminderRequest;
import com.destinyoracle.dto.response.ReminderResponse;

import java.util.List;
import java.util.UUID;

public interface ReminderService {

    ReminderResponse createReminder(UUID userId, CreateReminderRequest request);

    List<ReminderResponse> getActiveReminders(UUID userId);

    List<ReminderResponse> getUpcomingReminders(UUID userId, int hours);

    ReminderResponse completeReminder(UUID userId, UUID reminderId);

    ReminderResponse snoozeReminder(UUID userId, UUID reminderId, int minutes);

    void deleteReminder(UUID userId, UUID reminderId);
}
