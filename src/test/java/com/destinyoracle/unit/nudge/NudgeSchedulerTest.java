package com.destinyoracle.unit.nudge;

import com.destinyoracle.domain.plan.entity.*;
import com.destinyoracle.domain.plan.repository.*;
import com.destinyoracle.service.PushNotificationService;
import com.destinyoracle.scheduler.NudgeScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static com.destinyoracle.testutil.TestDataFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NudgeSchedulerTest {

    @Mock private PlanScheduleRepository scheduleRepo;
    @Mock private NudgeStateRepository nudgeStateRepo;
    @Mock private PushNotificationService pushService;

    @InjectMocks private NudgeScheduler nudgeScheduler;

    @Test
    void checkAndNudge_nothingScheduled_doesNothing() {
        when(scheduleRepo.findByDayOfWeekAndActiveTrue(any()))
            .thenReturn(List.of());

        nudgeScheduler.checkAndNudge();

        verifyNoInteractions(pushService);
    }

    @Test
    void checkAndNudge_onTime_sendsFirstNudge() {
        PlanSchedule schedule = mondayLegDay();
        schedule.setDayOfWeek(LocalDate.now().getDayOfWeek());
        // Set time to 5 minutes ago (just became due)
        schedule.setTimeOfDay(LocalTime.now().minusMinutes(5));

        NudgeState state = nudgeLevel(0); // no nudge sent yet

        when(scheduleRepo.findByDayOfWeekAndActiveTrue(any()))
            .thenReturn(List.of(schedule));
        when(nudgeStateRepo.findByUserIdAndPlanScheduleIdAndNudgeDate(any(), any(), any()))
            .thenReturn(Optional.of(state));

        nudgeScheduler.checkAndNudge();

        verify(pushService).sendToUser(eq(USER_ID), anyString(), anyString());
        assertThat(state.getNudgeLevel()).isEqualTo(1);
    }

    @Test
    void checkAndNudge_30minOverdue_escalates() {
        PlanSchedule schedule = mondayLegDay();
        schedule.setDayOfWeek(LocalDate.now().getDayOfWeek());
        schedule.setTimeOfDay(LocalTime.now().minusMinutes(35));

        NudgeState state = nudgeLevel(1);
        state.setLastNudgeAt(LocalDateTime.now().minusMinutes(30));

        when(scheduleRepo.findByDayOfWeekAndActiveTrue(any()))
            .thenReturn(List.of(schedule));
        when(nudgeStateRepo.findByUserIdAndPlanScheduleIdAndNudgeDate(any(), any(), any()))
            .thenReturn(Optional.of(state));

        nudgeScheduler.checkAndNudge();

        verify(pushService).sendToUser(eq(USER_ID), anyString(), anyString());
        assertThat(state.getNudgeLevel()).isEqualTo(2);
    }

    @Test
    void checkAndNudge_alreadyCompleted_skips() {
        PlanSchedule schedule = mondayLegDay();
        schedule.setDayOfWeek(LocalDate.now().getDayOfWeek());
        schedule.setTimeOfDay(LocalTime.now().minusMinutes(5));

        NudgeState state = nudgeLevel(1);
        state.setCompleted(true); // user marked done

        when(scheduleRepo.findByDayOfWeekAndActiveTrue(any()))
            .thenReturn(List.of(schedule));
        when(nudgeStateRepo.findByUserIdAndPlanScheduleIdAndNudgeDate(any(), any(), any()))
            .thenReturn(Optional.of(state));

        nudgeScheduler.checkAndNudge();

        verifyNoInteractions(pushService); // no nudge for completed items
    }

    @Test
    void checkAndNudge_level3_stopsNudging() {
        PlanSchedule schedule = mondayLegDay();
        schedule.setDayOfWeek(LocalDate.now().getDayOfWeek());
        schedule.setTimeOfDay(LocalTime.now().minusMinutes(90));

        NudgeState state = nudgeLevel(3); // already sent final nudge

        when(scheduleRepo.findByDayOfWeekAndActiveTrue(any()))
            .thenReturn(List.of(schedule));
        when(nudgeStateRepo.findByUserIdAndPlanScheduleIdAndNudgeDate(any(), any(), any()))
            .thenReturn(Optional.of(state));

        nudgeScheduler.checkAndNudge();

        verifyNoInteractions(pushService);
        assertThat(state.getNudgeLevel()).isEqualTo(3); // unchanged
    }
}
