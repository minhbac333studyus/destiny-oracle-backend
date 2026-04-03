package com.destinyoracle.domain.card.service.impl;

import com.destinyoracle.dto.response.UserResponse;
import com.destinyoracle.domain.user.entity.AppUser;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.domain.user.repository.UserRepository;
import com.destinyoracle.domain.card.service.AvatarService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private final UserRepository userRepository;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini.model:gemini-2.5-flash-image}")
    private String imagenModel;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final String CHIBI_PROMPT =
            "Convert this person's photo into a cute chibi anime character. " +
            "Keep the same hairstyle color and style, facial features, and any distinctive characteristics. " +
            "The chibi should have big expressive eyes, small body proportions, and be in a clean illustration " +
            "style with transparent or simple background. Only output the character, no text, no background scene. " +
            "The style should be suitable for a tarot card character avatar.";

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse uploadAvatarAndGenerateChibi(UUID userId, MultipartFile file) {
        validateFile(file);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        log.info("Avatar upload for user={} file={} size={}KB",
                userId, file.getOriginalFilename(), file.getSize() / 1024);

        try {
            // 1. Save original avatar to disk
            byte[] avatarBytes = file.getBytes();
            String avatarUrl = saveFile(userId, "avatar-original.png", avatarBytes);
            log.info("  Saved original avatar: {}", avatarUrl);

            // 2. Call Gemini to generate chibi
            String chibiUrl = generateAndSaveChibi(userId, avatarBytes, file.getContentType());
            log.info("  Saved chibi avatar: {}", chibiUrl);

            // 3. Update user entity
            user.setAvatarUrl(avatarUrl);
            user.setChibiUrl(chibiUrl);
            user.setChibiGeneratedAt(Instant.now());
            AppUser saved = userRepository.save(user);

            log.info("  User {} updated with avatar + chibi URLs", userId);
            return toResponse(saved);

        } catch (IOException | InterruptedException e) {
            log.error("  Avatar processing failed for user={}: {}", userId, e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to process avatar: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public UserResponse generateChibiFromAvatar(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        log.info("Re-generating chibi from existing avatar for user={}", userId);

        // Read existing avatar file from disk
        Path avatarPath = resolveUserDir(userId).resolve("avatar-original.png");
        if (!Files.exists(avatarPath)) {
            throw new RuntimeException("No existing avatar found for user " + userId
                    + ". Upload an avatar first using the upload endpoint.");
        }

        try {
            byte[] avatarBytes = Files.readAllBytes(avatarPath);

            // Call Gemini to generate chibi
            String chibiUrl = generateAndSaveChibi(userId, avatarBytes, "image/png");
            log.info("  Saved new chibi avatar: {}", chibiUrl);

            // Update user entity
            user.setChibiUrl(chibiUrl);
            user.setChibiGeneratedAt(Instant.now());
            AppUser saved = userRepository.save(user);

            log.info("  User {} updated with new chibi URL", userId);
            return toResponse(saved);

        } catch (IOException | InterruptedException e) {
            log.error("  Chibi re-generation failed for user={}: {}", userId, e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to generate chibi: " + e.getMessage(), e);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Invalid file type: " + contentType + ". Allowed: png, jpg, jpeg, webp");
        }
    }

    private String generateAndSaveChibi(UUID userId, byte[] avatarBytes, String mimeType)
            throws IOException, InterruptedException {

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("  GEMINI_API_KEY not set — skipping chibi generation");
            throw new RuntimeException("Gemini API key not configured. Cannot generate chibi.");
        }

        String imageBase64 = Base64.getEncoder().encodeToString(avatarBytes);
        String chibiBase64 = callGeminiForChibi(imageBase64, mimeType != null ? mimeType : "image/png");

        byte[] chibiBytes = Base64.getDecoder().decode(chibiBase64);
        return saveFile(userId, "chibi-avatar.png", chibiBytes);
    }

    /**
     * Calls Gemini to convert an avatar photo into a chibi character.
     * Same HTTP pattern as CardImageGenerationServiceImpl.callGeminiImagen().
     */
    private String callGeminiForChibi(String imageBase64, String mimeType)
            throws IOException, InterruptedException {

        ObjectMapper mapper = new ObjectMapper();

        // Build parts: image first, then text prompt
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("inlineData", Map.of("mimeType", mimeType, "data", imageBase64)));
        parts.add(Map.of("text", CHIBI_PROMPT));

        Map<String, Object> generationConfig = Map.of(
                "responseModalities", List.of("IMAGE")
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", generationConfig
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + imagenModel + ":generateContent?key=" + geminiApiKey;

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        log.info("  Calling Gemini for chibi conversion (model={})...", imagenModel);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode candidates = root.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No candidates in Gemini response: " + response.body());
        }

        JsonNode responseParts = candidates.get(0).get("content").get("parts");
        for (JsonNode part : responseParts) {
            JsonNode inlineData = part.get("inlineData");
            if (inlineData != null) {
                log.info("  Gemini returned chibi image successfully");
                return inlineData.get("data").asText();
            }
            if (part.get("text") != null) {
                log.warn("  Gemini returned TEXT instead of image: {}",
                        part.get("text").asText().substring(0, Math.min(200, part.get("text").asText().length())));
            }
        }

        JsonNode finishReason = candidates.get(0).get("finishReason");
        log.error("  Gemini finishReason={} — no image in response", finishReason);
        throw new IOException("No image data found in Gemini response. finishReason=" + finishReason);
    }

    /** Saves a file to static/generated/{userId}/{filename} and returns the URL path. */
    private String saveFile(UUID userId, String filename, byte[] data) throws IOException {
        Path dir = resolveUserDir(userId);
        Files.createDirectories(dir);

        Path filePath = dir.resolve(filename);
        Files.write(filePath, data);

        String url = "/generated/" + userId + "/" + filename;
        log.info("  File saved: {} ({} KB)", url, data.length / 1024);
        return url;
    }

    private Path resolveUserDir(UUID userId) {
        return Paths.get(System.getProperty("user.dir"),
                "src", "main", "resources", "static", "generated", userId.toString());
    }

    private UserResponse toResponse(AppUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .chibiUrl(user.getChibiUrl())
                .onboardingComplete(user.isOnboardingComplete())
                .joinedAt(user.getJoinedAt())
                .timezone(user.getTimezone())
                .notificationsEnabled(user.isNotificationsEnabled())
                .dailyReminderTime(user.getDailyReminderTime())
                .build();
    }
}
