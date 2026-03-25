package com.destinyoracle.service.impl;

import com.destinyoracle.dto.request.ActivityRequest;
import com.destinyoracle.dto.response.ActivityResponse;
import com.destinyoracle.entity.ParsedActivity;
import com.destinyoracle.repository.ParsedActivityRepository;
import com.destinyoracle.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ParsedActivityRepository activityRepository;

    // ── Keyword map ────────────────────────────────────────────────────────────

    private static final Map<String, List<String>> ASPECT_KEYWORDS;

    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("health",        List.of("run", "workout", "gym", "exercise", "walk", "yoga",
                                         "ate", "eat", "cook", "sleep", "swim"));
        map.put("career",        List.of("work", "worked", "project", "meeting", "code", "resume",
                                         "interview", "portfolio", "shipped"));
        map.put("finances",      List.of("save", "budget", "invest", "spending", "expense",
                                         "money", "bill", "debt"));
        map.put("relationships", List.of("friend", "date", "partner", "call", "called",
                                         "hangout", "texted"));
        map.put("family",        List.of("family", "kids", "children", "mom", "dad",
                                         "parent", "dinner"));
        map.put("learning",      List.of("read", "study", "learn", "course", "tutorial",
                                         "podcast", "book"));
        map.put("creativity",    List.of("draw", "paint", "music", "write", "wrote", "design",
                                         "photo", "art", "sing", "guitar", "piano"));
        map.put("spirituality",  List.of("meditate", "pray", "journal", "grateful",
                                         "gratitude", "reflect"));
        map.put("lifestyle",     List.of("travel", "adventure", "hobby", "relax", "vacation",
                                         "hike", "fun", "game"));
        map.put("legacy",        List.of("volunteer", "donate", "mentor", "teach", "community",
                                         "help", "helped"));
        ASPECT_KEYWORDS = Collections.unmodifiableMap(map);
    }

    private static final Map<String, String> ASPECT_LABELS = Map.ofEntries(
            Map.entry("health",        "Health & Fitness"),
            Map.entry("career",        "Career & Purpose"),
            Map.entry("finances",      "Finances"),
            Map.entry("relationships", "Relationships"),
            Map.entry("family",        "Family"),
            Map.entry("learning",      "Learning & Growth"),
            Map.entry("creativity",    "Creativity"),
            Map.entry("spirituality",  "Spirituality"),
            Map.entry("lifestyle",     "Lifestyle"),
            Map.entry("legacy",        "Legacy & Impact")
    );

    private static final Random RANDOM = new Random();

    // ── Public methods ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ActivityResponse logActivity(UUID userId, ActivityRequest request) {
        String rawInput = request.getRawInput();
        String aspectKey = detectAspectKey(rawInput);
        String aspectLabel = ASPECT_LABELS.getOrDefault(aspectKey, "Lifestyle");
        int xpGained = 5 + RANDOM.nextInt(16); // 5..20 inclusive

        ParsedActivity activity = ParsedActivity.builder()
                .userId(userId)
                .rawInput(rawInput)
                .aspectKey(aspectKey)
                .aspectLabel(aspectLabel)
                .activitySummary(buildSummary(rawInput, aspectLabel))
                .xpGained(xpGained)
                .build();

        ParsedActivity saved = activityRepository.save(activity);
        log.info("Logged activity for user {}: aspect={}, xp={}", userId, aspectKey, xpGained);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> getActivities(UUID userId, String aspectKey) {
        List<ParsedActivity> activities;
        if (aspectKey == null || aspectKey.isBlank()) {
            activities = activityRepository.findAllByUserIdOrderByCreatedAtDesc(
                    userId, PageRequest.of(0, 50));
        } else {
            activities = activityRepository.findAllByUserIdAndAspectKeyOrderByCreatedAtDesc(
                    userId, aspectKey);
        }
        return activities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String detectAspectKey(String rawInput) {
        String lower = rawInput.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : ASPECT_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "lifestyle";
    }

    private String buildSummary(String rawInput, String aspectLabel) {
        String trimmed = rawInput.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 117) + "...";
    }

    private ActivityResponse toResponse(ParsedActivity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .rawInput(activity.getRawInput())
                .aspectKey(activity.getAspectKey())
                .aspectLabel(activity.getAspectLabel())
                .activitySummary(activity.getActivitySummary())
                .xpGained(activity.getXpGained())
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
