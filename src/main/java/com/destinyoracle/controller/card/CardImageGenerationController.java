package com.destinyoracle.controller.card;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.dto.response.*;
import com.destinyoracle.service.CardImageGenerationService;
import com.destinyoracle.service.GenerationJobService;
import com.destinyoracle.service.ImagePromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Image Generation", description = "AI image pipeline — Claude prompts + Gemini Imagen generation")
public class CardImageGenerationController {

    private final ImagePromptService         imagePromptService;
    private final CardImageGenerationService imageGenerationService;
    private final GenerationJobService       jobService;
    private final AppProperties              appProperties;

    // ── Prompt generation ─────────────────────────────────────────────────────

    /**
     * POST /api/v1/cards/{cardId}/generate-prompts
     * Step 1 only — Claude writes 6 stage prompts, persisted to DB.
     */
    @PostMapping("/{cardId}/generate-prompts")
    @Operation(
        summary = "Generate image prompts (Claude)",
        description = "Calls Claude AI to write 6 stage-specific image prompts for the card. " +
                      "Prompts are persisted to card_stage_content.image_prompt so they can be " +
                      "reused by generate-images without calling Claude again. " +
                      "Requires ANTHROPIC_API_KEY env var."
    )
    public ResponseEntity<ApiResponse<ImagePromptResponse>> generatePrompts(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {

        ImagePromptResponse response = imagePromptService
                .generatePromptsForCard(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── Full pipeline ─────────────────────────────────────────────────────────

    /**
     * POST /api/v1/cards/{cardId}/generate-images
     * Full pipeline: Claude prompts → Gemini images (all 6 stages, parallel).
     * Creates a GenerationJob for real-time progress polling.
     */
    @PostMapping("/{cardId}/generate-images")
    @Operation(
        summary = "Generate all stage images (full pipeline)",
        description = "Full tracked pipeline: Claude generates 6 prompts (persisted to DB) → " +
                      "Gemini Imagen generates 6 images in parallel via virtual threads → " +
                      "stores in GCS → persists URLs in DB. " +
                      "A GenerationJob is created — poll GET /{cardId}/jobs/latest for progress. " +
                      "Requires ANTHROPIC_API_KEY + GOOGLE_CLOUD_PROJECT_ID. May take ~60–90s."
    )
    public ResponseEntity<ApiResponse<List<GeneratedImageResponse>>> generateAllImages(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {

        List<GeneratedImageResponse> results = imageGenerationService
                .generateAllStageImages(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * POST /api/v1/cards/{cardId}/generate-images/{stage}
     * Re-generate the image for a single stage only (no job tracking).
     */
    @PostMapping("/{cardId}/generate-images/{stage}")
    @Operation(
        summary = "Regenerate single stage image",
        description = "Regenerates the image for one specific stage. " +
                      "Loads the saved prompt from card_stage_content.image_prompt in DB — " +
                      "only calls Claude if no prompt has been saved yet for this card. " +
                      "Useful after a card evolves to a new stage or to re-roll a result."
    )
    public ResponseEntity<ApiResponse<GeneratedImageResponse>> generateSingleStageImage(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId,
            @PathVariable String stage) {

        GeneratedImageResponse result = imageGenerationService
                .generateStageImage(resolve(userId), cardId, stage);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * POST /api/v1/cards/generate-images/all
     *
     * Generate 6 stage images for ALL of the user's aspect cards in one call.
     * Each aspect card runs its own full pipeline (Claude prompts → Gemini images).
     * Cards are processed one at a time (sequential per aspect); within each aspect
     * all 6 stage images are generated in parallel via virtual threads.
     *
     * Use case: first-time setup — one button to generate art for all starter aspects.
     */
    @PostMapping("/generate-images/all")
    @Operation(
        summary = "Generate images for all aspects",
        description = "Runs the full pipeline (Claude prompts → Gemini images) for ALL of the user's aspect cards. " +
                      "One aspect at a time (sequential); 6 stage images per aspect run in parallel. " +
                      "Each aspect gets its own GenerationJob — poll /{cardId}/jobs/latest per aspect. " +
                      "Returns one result per aspect with status 'completed' or 'failed'."
    )
    public ResponseEntity<ApiResponse<List<BatchImageResult>>> generateAllAspects(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        List<BatchImageResult> results = imageGenerationService.generateAllUserCards(resolve(userId));
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ── Job polling ───────────────────────────────────────────────────────────

    /**
     * GET /api/v1/cards/{cardId}/jobs/latest
     * Returns the most recent GenerationJob for a card with all 12 step statuses.
     * Poll this every 2–3 seconds while a pipeline is running to show real-time progress.
     *
     * When job.status == "COMPLETED" or "FAILED", stop polling.
     */
    @GetMapping("/{cardId}/jobs/latest")
    @Operation(
        summary = "Get latest generation job (poll for progress)",
        description = "Returns the most recent GenerationJob for this card, including all 12 step statuses. " +
                      "Poll every 2–3s to show real-time pipeline progress in the UI. " +
                      "Each step has: phase (PROMPT|IMAGE), stage, status (WAITING|RUNNING|DONE|FAILED|SKIPPED), message. " +
                      "Stop polling when job.status is COMPLETED or FAILED."
    )
    public ResponseEntity<ApiResponse<GenerationJobResponse>> getLatestJob(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {

        GenerationJobResponse job = jobService.getLatestJob(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * GET /api/v1/cards/{cardId}/jobs/{jobId}
     * Get a specific job by ID — for direct linking or history lookup.
     */
    @GetMapping("/{cardId}/jobs/{jobId}")
    @Operation(
        summary = "Get specific generation job",
        description = "Fetches a specific GenerationJob by its ID. All 12 steps included."
    )
    public ResponseEntity<ApiResponse<GenerationJobResponse>> getJob(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId,
            @PathVariable UUID jobId) {

        GenerationJobResponse job = jobService.getJob(resolve(userId), jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * GET /api/v1/cards/{cardId}/jobs
     * All jobs for a card, newest first — for job history display.
     */
    @GetMapping("/{cardId}/jobs")
    @Operation(
        summary = "List all generation jobs for a card",
        description = "Returns all GenerationJobs for this card, newest first. Useful for job history."
    )
    public ResponseEntity<ApiResponse<List<GenerationJobResponse>>> listJobs(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {

        List<GenerationJobResponse> jobs = jobService.listJobs(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
