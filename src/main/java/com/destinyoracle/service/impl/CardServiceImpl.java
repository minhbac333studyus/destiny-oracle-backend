package com.destinyoracle.service.impl;

import com.destinyoracle.dto.request.AddCardRequest;
import com.destinyoracle.dto.request.UpdateCardRequest;
import com.destinyoracle.dto.response.*;
import com.destinyoracle.entity.*;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.repository.*;
import com.destinyoracle.event.CardCreatedEvent;
import com.destinyoracle.service.CardService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final DestinyCardRepository          cardRepository;
    private final HabitRepository                habitRepository;
    private final HabitCompletionRepository      habitCompletionRepository;
    private final UserRepository                 userRepository;
    private final AspectDefinitionRepository     aspectDefinitionRepository;
    private final StageContentGenerationServiceImpl stageContentGenerationService;
    private final ApplicationEventPublisher         eventPublisher;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SpreadCardSummaryResponse> getAllCards(UUID userId) {
        return cardRepository.findAllByUserId(userId).stream()
                .map(card -> SpreadCardSummaryResponse.builder()
                        .id(card.getId())
                        .aspectKey(card.getAspectKey())
                        .aspectLabel(card.getAspectLabel())
                        .aspectIcon(card.getAspectIcon())
                        .cardTitle(resolveTitle(card))
                        .stage(card.getCurrentStage().name())
                        .imageUrl(card.getImageUrl())
                        .progressPercent(card.getStageProgressPercent())
                        .streakDays(card.getCurrentStreak())
                        .hasUnreadUpdate(false)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CardDetailResponse getCard(UUID userId, UUID cardId) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));
        return buildCardDetailResponse(card, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableAspectResponse> getAvailableAspects(UUID userId) {
        // Which aspect keys does this user already have?
        Set<String> userKeys = cardRepository.findAllByUserId(userId).stream()
                .map(DestinyCard::getAspectKey)
                .collect(Collectors.toSet());

        return aspectDefinitionRepository.findAllByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(def -> AvailableAspectResponse.builder()
                        .key(def.getAspectKey())
                        .label(def.getLabel())
                        .icon(def.getIcon())
                        .alreadyAdded(userKeys.contains(def.getAspectKey()))
                        .build())
                .collect(Collectors.toList());
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CardDetailResponse addCard(UUID userId, AddCardRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Auto-generate a unique aspect_key from the user's label
        // e.g. "My battle with lymphoma" → "my-battle-with-lymphoma-a3f2"
        String slugBase  = request.getAspectLabel().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
        String aspectKey = (slugBase.length() > 40 ? slugBase.substring(0, 40) : slugBase) + "-" + uniqueSuffix;

        String icon = nonBlank(request.getIcon(), "✨");

        // Sort order = current card count + 1
        int sortOrder = cardRepository.findAllByUserId(userId).size() + 1;

        DestinyCard card = DestinyCard.builder()
                .user(user)
                .aspectKey(aspectKey)
                .aspectLabel(request.getAspectLabel())
                .aspectIcon(icon)
                .isCustomAspect(true)
                .sortOrder(sortOrder)
                .fearOriginal(request.getFearText())
                .dreamOriginal(request.getDreamText())
                .currentStage(CardStage.storm)
                .stageProgressPercent(0)
                .imageUrl("assets/health-user1.png")
                .lastUpdated(Instant.now())
                .build();

        card = cardRepository.save(card);
        log.info("Added aspect '{}' ({}) for user {} — triggering stage content generation", request.getAspectLabel(), aspectKey, userId);

        UUID cardId = card.getId();

        // ── Core AI Step 1: Generate all 6 stage narratives from fear + dream ──
        // Claude writes title, tagline, lore for Storm → Legend grounded in
        // the user's own words. This is what makes every card feel personal.
        // If Claude is unavailable (no API key), fallback content is used automatically.
        try {
            stageContentGenerationService.generateStageContent(userId, cardId);
        } catch (Exception e) {
            log.warn("Stage content generation failed for card={} — card still created with fallback content: {}",
                    cardId, e.getMessage());
        }

        // Fire image generation asynchronously — card creation returns immediately
        eventPublisher.publishEvent(new CardCreatedEvent(userId, cardId));
        log.info("Published CardCreatedEvent — image pipeline will run in background");

        card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));
        return buildCardDetailResponse(card, userId);
    }

    @Override
    @Transactional
    public CardDetailResponse updateCard(UUID userId, UUID cardId, UpdateCardRequest request) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        if (nonBlankStr(request.getFearOriginal()))  card.setFearOriginal(request.getFearOriginal());
        if (nonBlankStr(request.getDreamOriginal())) card.setDreamOriginal(request.getDreamOriginal());

        // All cards (built-in and custom) can have their label and icon updated by the user
        if (nonBlankStr(request.getAspectLabel())) card.setAspectLabel(request.getAspectLabel());
        if (nonBlankStr(request.getIcon()))        card.setAspectIcon(request.getIcon());

        card.setLastUpdated(Instant.now());
        card = cardRepository.save(card);

        UUID savedId = card.getId();
        card = cardRepository.findByIdAndUserIdWithDetails(savedId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", savedId));
        return buildCardDetailResponse(card, userId);
    }

    @Override
    @Transactional
    public void removeCard(UUID userId, UUID cardId) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));
        cardRepository.delete(card);
        log.info("Removed aspect card {} for user {}", cardId, userId);
    }

    @Override
    @Transactional
    public void completeHabit(UUID userId, UUID cardId, UUID habitId, boolean completed) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        Habit habit = habitRepository.findByIdAndCardId(habitId, cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit", "id", habitId));

        LocalDate today = LocalDate.now();

        if (completed) {
            if (!habitCompletionRepository.existsByHabitIdAndCompletedOn(habitId, today)) {
                habitCompletionRepository.save(HabitCompletion.builder()
                        .habit(habit)
                        .userId(userId)
                        .completedOn(today)
                        .build());
                card.setTotalCheckIns(card.getTotalCheckIns() + 1);
                cardRepository.save(card);
            }
        } else {
            habitCompletionRepository.deleteByHabitIdAndCompletedOn(habitId, today);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CardDetailResponse buildCardDetailResponse(DestinyCard card, UUID userId) {
        List<Habit> habits = card.getHabits();

        List<UUID> habitIds = habits.stream().map(Habit::getId).collect(Collectors.toList());
        Set<UUID> completedToday = habitIds.isEmpty()
                ? Collections.emptySet()
                : habitCompletionRepository.findCompletedHabitIds(habitIds, LocalDate.now());

        List<CardHabitResponse> habitResponses = habits.stream()
                .map(h -> CardHabitResponse.builder()
                        .id(h.getId())
                        .text(h.getText())
                        .frequency(h.getFrequency())
                        .completedToday(completedToday.contains(h.getId()))
                        .streakDays(h.getStreakDays())
                        .build())
                .collect(Collectors.toList());

        // Build ordered stageContent map (storm → legend)
        Map<String, StageContentEntry> stageContentMap = new LinkedHashMap<>();
        card.getStageContents().stream()
                .sorted(Comparator.comparingInt(sc -> sc.getStage().ordinal()))
                .forEach(sc -> stageContentMap.put(sc.getStage().name(), StageContentEntry.builder()
                        .title(sc.getTitle())
                        .tagline(sc.getTagline())
                        .lore(sc.getLore())
                        .actionScene(sc.getActionScene())
                        .build()));

        StageContentEntry currentContent = stageContentMap.get(card.getCurrentStage().name());
        String cardTitle   = currentContent != null ? currentContent.getTitle()   : card.getAspectLabel();
        String cardTagline = currentContent != null ? currentContent.getTagline() : "";
        String loreText    = currentContent != null ? currentContent.getLore()    : "";

        List<CardImageResponse> imageHistory = card.getImageHistory().stream()
                .map(img -> CardImageResponse.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .stage(img.getStage().name())
                        .generatedAt(img.getGeneratedAt())
                        .promptSummary(img.getPromptSummary())
                        .build())
                .collect(Collectors.toList());

        return CardDetailResponse.builder()
                .id(card.getId())
                .aspectKey(card.getAspectKey())
                .aspectLabel(card.getAspectLabel())
                .aspectIcon(card.getAspectIcon())
                .cardTitle(cardTitle)
                .cardTagline(cardTagline)
                .loreText(loreText)
                .imageUrl(card.getImageUrl())
                .lastUpdated(card.getLastUpdated())
                .stats(CardStatsResponse.builder()
                        .currentStage(card.getCurrentStage().name())
                        .stageProgressPercent(card.getStageProgressPercent())
                        .totalCheckIns(card.getTotalCheckIns())
                        .longestStreak(card.getLongestStreak())
                        .currentStreak(card.getCurrentStreak())
                        .daysAtCurrentStage(card.getDaysAtCurrentStage())
                        .build())
                .habits(habitResponses)
                .imageHistory(imageHistory)
                .stageContent(stageContentMap)
                .fearOriginal(card.getFearOriginal())
                .dreamOriginal(card.getDreamOriginal())
                .build();
    }

    private String resolveTitle(DestinyCard card) {
        return card.getStageContents().stream()
                .filter(sc -> sc.getStage() == card.getCurrentStage())
                .map(CardStageContent::getTitle)
                .findFirst()
                .orElse(card.getAspectLabel());
    }

    private String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private boolean nonBlankStr(String value) {
        return value != null && !value.isBlank();
    }

    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;
        String[] words = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
