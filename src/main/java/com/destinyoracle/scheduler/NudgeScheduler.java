package com.destinyoracle.scheduler;

import com.destinyoracle.domain.plan.entity.NudgeState;
import com.destinyoracle.domain.plan.entity.PlanSchedule;
import com.destinyoracle.domain.plan.repository.NudgeStateRepository;
import com.destinyoracle.domain.plan.repository.PlanScheduleRepository;
import com.destinyoracle.domain.notification.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Runs every 5 minutes. Checks scheduled plans and escalates nudges:
 *   Level 0 → 1: "Time for {plan}!"
 *   Level 1 → 2: "+30 min. Quick version?"
 *   Level 2 → 3: "+60 min. Skipped today. Reschedule?"
 *   Level 3: Stop nudging.
 */
@Component
public class NudgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(NudgeScheduler.class);

    private final PlanScheduleRepository scheduleRepo;
    private final NudgeStateRepository nudgeRepo;
    private final PushNotificationService pushService;

    public NudgeScheduler(
        PlanScheduleRepository scheduleRepo,
        NudgeStateRepository nudgeRepo,
        PushNotificationService pushService
    ) {
        this.scheduleRepo = scheduleRepo;
        this.nudgeRepo = nudgeRepo;
        this.pushService = pushService;
    }

    @Scheduled(fixedRate = 300_000)  // Every 5 minutes
    @Transactional
    public void checkAndNudge() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now = LocalTime.now();
        LocalDate todayDate = LocalDate.now();

        List<PlanSchedule> schedules = scheduleRepo.findByDayOfWeekAndActiveTrue(today);

        for (PlanSchedule schedule : schedules) {
            if (schedule.getTimeOfDay() == null) continue;
            if (now.isBefore(schedule.getTimeOfDay())) continue;  // Not time yet

            NudgeState state = nudgeRepo
                .findByUserIdAndPlanScheduleIdAndNudgeDate(
                    schedule.getUserId(), schedule.getId(), todayDate)
                .orElseGet(() -> NudgeState.builder()
                    .userId(schedule.getUserId())
                    .planScheduleId(schedule.getId())
                    .nudgeDate(todayDate)
                    .build());

            if (state.getCompleted() || state.getSkipped()) continue;
            if (state.getNudgeLevel() >= 3) continue;

            // Check timing for escalation
            int minutesPast = (int) java.time.Duration.between(schedule.getTimeOfDay(), now).toMinutes();

            int newLevel = 0;
            if (minutesPast >= 60) newLevel = 3;
            else if (minutesPast >= 30) newLevel = 2;
            else newLevel = 1;

            if (newLevel > state.getNudgeLevel()) {
                state.setNudgeLevel(newLevel);
                state.setLastNudgeAt(LocalDateTime.now());
                nudgeRepo.save(state);

                String planName = schedule.getSavedPlan().getName();
                String message = switch (newLevel) {
                    case 1 -> "Time for " + planName + "! 💪";
                    case 2 -> "30 min past " + planName + " time. Quick version?";
                    case 3 -> "Skipped " + planName + " today. Reschedule for tomorrow?";
                    default -> "";
                };

                pushService.sendToUser(schedule.getUserId(), "Destiny Oracle", message);
                log.debug("Nudge level {} sent to user {} for plan '{}'",
                    newLevel, schedule.getUserId(), planName);
            }
        }
    }
}
