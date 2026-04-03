package com.destinyoracle.unit.plan;

import com.destinyoracle.domain.plan.entity.SavedPlan;
import com.destinyoracle.domain.plan.repository.PlanScheduleRepository;
import com.destinyoracle.domain.plan.repository.SavedPlanRepository;
import com.destinyoracle.dto.request.SavePlanRequest;
import com.destinyoracle.dto.request.UpdatePlanRequest;
import com.destinyoracle.dto.response.SavedPlanResponse;
import com.destinyoracle.domain.plan.service.impl.SavedPlanServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static com.destinyoracle.testutil.TestDataFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedPlanServiceTest {

    @Mock private SavedPlanRepository planRepo;
    @Mock private PlanScheduleRepository scheduleRepo;
    @InjectMocks private SavedPlanServiceImpl planService;

    @Test
    void savePlan_newPlan_createsWithSlug() {
        var request = new SavePlanRequest(
            "Leg day", SavedPlan.PlanType.WORKOUT,
            "4 exercises", "{\"exercises\":[]}", null
        );

        when(planRepo.findByUserIdAndSlugAndActiveTrue(USER_ID, "leg-day"))
            .thenReturn(Optional.empty());
        when(planRepo.save(any())).thenAnswer(inv -> {
            SavedPlan p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        SavedPlanResponse response = planService.savePlan(USER_ID, request);

        assertThat(response.slug()).isEqualTo("leg-day");
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.name()).isEqualTo("Leg day");
    }

    @Test
    void savePlan_duplicateSlug_throws() {
        var request = new SavePlanRequest(
            "Leg day", SavedPlan.PlanType.WORKOUT,
            "desc", "{}", null
        );

        when(planRepo.findByUserIdAndSlugAndActiveTrue(USER_ID, "leg-day"))
            .thenReturn(Optional.of(legDayPlan()));

        assertThatThrownBy(() -> planService.savePlan(USER_ID, request))
            .hasMessageContaining("already exists");
    }

    @Test
    void updatePlan_overwrite_keepsSameVersion() {
        SavedPlan existing = legDayPlan();
        var request = new UpdatePlanRequest(
            null, null, "{\"exercises\":[{\"name\":\"New squat\"}]}", true
        );

        when(planRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedPlanResponse response = planService.updatePlan(USER_ID, existing.getId(), request);

        assertThat(response.version()).isEqualTo(1); // same version
        assertThat(response.content()).contains("New squat");
    }

    @Test
    void updatePlan_newVersion_incrementsVersion() {
        SavedPlan existing = legDayPlan();
        var request = new UpdatePlanRequest(
            null, null, "{\"exercises\":[{\"name\":\"Updated\"}]}", false
        );

        when(planRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedPlanResponse response = planService.updatePlan(USER_ID, existing.getId(), request);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.parentPlanId()).isEqualTo(existing.getId());
        // Old plan should be deactivated
        assertThat(existing.getActive()).isFalse();
    }

    @Test
    void slugify_generatesCorrectSlug() {
        assertThat(SavedPlan.slugify("Leg Day")).isEqualTo("leg-day");
        assertThat(SavedPlan.slugify("Leg Day v2")).isEqualTo("leg-day-v2");
        assertThat(SavedPlan.slugify("Morning Routine!")).isEqualTo("morning-routine");
    }
}
