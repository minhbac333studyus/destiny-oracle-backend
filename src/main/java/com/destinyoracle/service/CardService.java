package com.destinyoracle.service;

import com.destinyoracle.dto.request.AddCardRequest;
import com.destinyoracle.dto.request.UpdateCardRequest;
import com.destinyoracle.dto.response.AvailableAspectResponse;
import com.destinyoracle.dto.response.CardDetailResponse;
import com.destinyoracle.dto.response.SpreadCardSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface CardService {

    /** All cards the user currently has (their active spread) */
    List<SpreadCardSummaryResponse> getAllCards(UUID userId);

    /** Full detail for one card, including stageContent map and habits */
    CardDetailResponse getCard(UUID userId, UUID cardId);

    /** All 10 built-in aspects showing which ones the user has already added */
    List<AvailableAspectResponse> getAvailableAspects(UUID userId);

    /**
     * Add a new aspect card for the user.
     * Works for both built-in aspects (from the remaining 5+) and custom user-invented aspects.
     */
    CardDetailResponse addCard(UUID userId, AddCardRequest request);

    /**
     * Edit a card's fear text, dream text, or (for custom aspects) label and icon.
     * Built-in aspect labels/icons cannot be changed.
     */
    CardDetailResponse updateCard(UUID userId, UUID cardId, UpdateCardRequest request);

    /**
     * Remove an aspect card entirely (user decided they no longer want to track it).
     * Deletes all associated habits, stage content, and image history.
     */
    void removeCard(UUID userId, UUID cardId);

    /** Toggle a single habit's completion for today */
    void completeHabit(UUID userId, UUID cardId, UUID habitId, boolean completed);
}
