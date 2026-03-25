package com.destinyoracle.service.impl;

import com.destinyoracle.domain.plan.entity.PlanSchedule;
import com.destinyoracle.domain.plan.entity.SavedPlan;
import com.destinyoracle.domain.plan.repository.PlanScheduleRepository;
import com.destinyoracle.domain.plan.repository.SavedPlanRepository;
import com.destinyoracle.dto.request.CreateScheduleRequest;
import com.destinyoracle.dto.request.SavePlanRequest;
import com.destinyoracle.dto.request.UpdatePlanRequest;
import com.destinyoracle.dto.response.SavedPlanResponse;
import com.destinyoracle.service.SavedPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SavedPlanServiceImpl implements SavedPlanService {

    private final SavedPlanRepository planRepo;
    private final PlanScheduleRepository scheduleRepo;

    public SavedPlanServiceImpl(SavedPlanRepository planRepo, PlanScheduleRepository scheduleRepo) {
        this.planRepo = planRepo;
        this.scheduleRepo = scheduleRepo;
    }

    @Override
    @Transactional
    public SavedPlanResponse savePlan(UUID userId, SavePlanRequest request) {
        String slug = request.slug() != null ? request.slug() : SavedPlan.slugify(request.name());

        // Check if slug already exists
        var existing = planRepo.findByUserIdAndSlugAndActiveTrue(userId, slug);
        if (existing.isPresent()) {
            throw new RuntimeException("Plan with slug '" + slug + "' already exists. Use update instead.");
        }

        SavedPlan plan = SavedPlan.builder()
            .userId(userId)
            .name(request.name())
            .slug(slug)
            .type(request.type())
            .description(request.description())
            .content(request.content())
            .build();

        return toResponse(planRepo.save(plan));
    }

    @Override
    @Transactional
    public SavedPlanResponse updatePlan(UUID userId, UUID planId, UpdatePlanRequest request) {
        SavedPlan plan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found"));

        if (!plan.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        if (request.overwrite()) {
            // Overwrite current version
            if (request.name() != null) plan.setName(request.name());
            if (request.description() != null) plan.setDescription(request.description());
            if (request.content() != null) plan.setContent(request.content());
            return toResponse(planRepo.save(plan));
        } else {
            // Create new version — deactivate old
            plan.setActive(false);
            planRepo.save(plan);

            SavedPlan newVersion = SavedPlan.builder()
                .userId(userId)
                .name(request.name() != null ? request.name() : plan.getName())
                .slug(plan.getSlug())
                .type(plan.getType())
                .description(request.description() != null ? request.description() : plan.getDescription())
                .content(request.content() != null ? request.content() : plan.getContent())
                .version(plan.getVersion() + 1)
                .parentPlanId(plan.getId())
                .build();

            return toResponse(planRepo.save(newVersion));
        }
    }

    @Override
    public List<SavedPlanResponse> listPlans(UUID userId) {
        return planRepo.findByUserIdAndActiveTrue(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public SavedPlanResponse getPlan(UUID userId, UUID planId) {
        SavedPlan plan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found"));
        if (!plan.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        return toResponse(plan);
    }

    @Override
    public SavedPlanResponse getPlanBySlug(UUID userId, String slug) {
        return planRepo.findByUserIdAndSlugAndActiveTrue(userId, slug)
            .map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Plan not found with slug: " + slug));
    }

    @Override
    public List<SavedPlanResponse> getVersionHistory(UUID userId, String slug) {
        return planRepo.findVersionHistory(userId, slug).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public void deletePlan(UUID userId, UUID planId) {
        SavedPlan plan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found"));
        if (!plan.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        plan.setActive(false);
        planRepo.save(plan);
    }

    @Override
    @Transactional
    public SavedPlanResponse.ScheduleResponse addSchedule(UUID userId, UUID planId, CreateScheduleRequest request) {
        SavedPlan plan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found"));
        if (!plan.getUserId().equals(userId)) throw new RuntimeException("Access denied");

        PlanSchedule schedule = PlanSchedule.builder()
            .userId(userId)
            .savedPlan(plan)
            .dayOfWeek(request.dayOfWeek())
            .timeOfDay(request.timeOfDay())
            .repeatType(request.repeatType() != null ? request.repeatType() : "WEEKLY")
            .repeatCron(request.repeatCron())
            .notifyBefore(request.notifyBefore() != null ? request.notifyBefore() : true)
            .notifyMinutesBefore(request.notifyMinutesBefore() != null ? request.notifyMinutesBefore() : 15)
            .build();

        schedule = scheduleRepo.save(schedule);
        return toScheduleResponse(schedule);
    }

    @Override
    @Transactional
    public void removeSchedule(UUID userId, UUID scheduleId) {
        PlanSchedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        schedule.setActive(false);
        scheduleRepo.save(schedule);
    }

    @Override
    public List<SavedPlanResponse.ScheduleResponse> getSchedules(UUID userId, UUID planId) {
        return scheduleRepo.findBySavedPlanIdAndActiveTrue(planId).stream()
            .map(this::toScheduleResponse)
            .toList();
    }

    // ── Mappers ──────────────────────────────────────────

    private SavedPlanResponse toResponse(SavedPlan plan) {
        List<SavedPlanResponse.ScheduleResponse> schedules = plan.getSchedules().stream()
            .filter(PlanSchedule::getActive)
            .map(this::toScheduleResponse)
            .toList();

        return new SavedPlanResponse(
            plan.getId(), plan.getName(), plan.getSlug(), plan.getType(),
            plan.getDescription(), plan.getContent(), plan.getVersion(),
            plan.getActive(), plan.getParentPlanId(), schedules,
            plan.getCreatedAt(), plan.getUpdatedAt()
        );
    }

    private SavedPlanResponse.ScheduleResponse toScheduleResponse(PlanSchedule s) {
        return new SavedPlanResponse.ScheduleResponse(
            s.getId(),
            s.getDayOfWeek() != null ? s.getDayOfWeek().name() : null,
            s.getTimeOfDay() != null ? s.getTimeOfDay().toString() : null,
            s.getRepeatType(), s.getNotifyBefore(),
            s.getNotifyMinutesBefore(), s.getActive()
        );
    }
}
