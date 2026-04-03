package com.destinyoracle.unit.task;

import com.destinyoracle.domain.task.entity.*;
import com.destinyoracle.domain.task.repository.*;
import com.destinyoracle.domain.task.service.impl.TaskServiceImpl;
import com.destinyoracle.shared.xp.XpCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static com.destinyoracle.testutil.TestDataFactory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskStepToggleTest {

    @Mock private TaskRepository taskRepo;
    @Mock private TaskStepRepository stepRepo;
    @Mock private XpCalculator xpCalculator;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TaskServiceImpl taskService;

    @Test
    void toggleStep_incomplete_marksComplete() {
        Task task = activeTask();
        TaskStep step = task.getSteps().get(3); // step 4, not yet completed
        assertThat(step.getCompleted()).isFalse();

        when(stepRepo.findById(step.getId())).thenReturn(Optional.of(step));

        taskService.toggleStep(USER_ID, step.getId());

        assertThat(step.getCompleted()).isTrue();
        assertThat(step.getCompletedAt()).isNotNull();
        assertThat(task.getCompletedSteps()).isEqualTo(4); // was 3, now 4
    }

    @Test
    void toggleStep_alreadyComplete_unmarks() {
        Task task = activeTask();
        TaskStep step = task.getSteps().get(0); // step 1, already completed
        assertThat(step.getCompleted()).isTrue();

        when(stepRepo.findById(step.getId())).thenReturn(Optional.of(step));

        taskService.toggleStep(USER_ID, step.getId());

        assertThat(step.getCompleted()).isFalse();
        assertThat(task.getCompletedSteps()).isEqualTo(2); // was 3, now 2
    }

    @Test
    void toggleStep_lastStep_completesTask() {
        Task task = activeTask();
        task.setCompletedSteps(7); // 7 of 8 done
        task.setTotalSteps(8);
        TaskStep lastStep = task.getSteps().get(7);
        lastStep.setCompleted(false);

        when(stepRepo.findById(lastStep.getId())).thenReturn(Optional.of(lastStep));

        taskService.toggleStep(USER_ID, lastStep.getId());

        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
        assertThat(task.getCompletedSteps()).isEqualTo(8);
    }
}
