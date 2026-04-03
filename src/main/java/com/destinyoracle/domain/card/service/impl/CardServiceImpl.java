package com.destinyoracle.domain.card.service.impl;

import com.destinyoracle.dto.request.AddCardRequest;
import com.destinyoracle.dto.request.UpdateCardRequest;
import com.destinyoracle.dto.response.*;
import com.destinyoracle.domain.card.entity.*;
import com.destinyoracle.domain.user.entity.*;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.domain.card.repository.*;
import com.destinyoracle.domain.user.repository.*;
import com.destinyoracle.domain.card.event.CardCreatedEvent;
import com.destinyoracle.domain.card.service.CardService;
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

    private final DestinyCardRepository      cardRepository;
    private final UserRepository             userRepository;
    private final AspectDefinitionRepository aspectDefinitionRepository;
    private final ApplicationEventPublisher  eventPublisher;

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
                        .cardTitle(card.getAspectLabel())
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
        log.info("Added aspect '{}' ({}) for user {}", request.getAspectLabel(), aspectKey, userId);

        UUID cardId = card.getId();

        // Fire image generation asynchronously — card creation returns immediately
        // The image pipeline (ImagePromptService → ImageProvider) handles everything:
        // Claude generates image prompts from fear text + stage specs, then Gemini renders images.
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

    // ── Private helpers ───────────────────────────────────────────────────────

    private CardDetailResponse buildCardDetailResponse(DestinyCard card, UUID userId) {
        return CardDetailResponse.builder()
                .id(card.getId())
                .aspectKey(card.getAspectKey())
                .aspectLabel(card.getAspectLabel())
                .aspectIcon(card.getAspectIcon())
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
                .fearOriginal(card.getFearOriginal())
                .dreamOriginal(card.getDreamOriginal())
                .build();
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
