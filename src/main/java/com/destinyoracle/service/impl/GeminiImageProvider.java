package com.destinyoracle.service.impl;

import com.destinyoracle.service.ImageProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * Gemini (Google AI) image generation provider.
 * Uses the Generative Language REST API with API key auth.
 */
@Slf4j
@Component("gemini")
public class GeminiImageProvider implements ImageProvider {

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-2.0-flash-preview-image-generation}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String providerName() {
        return "Gemini " + model;
    }

    @Override
    public String generate(String prompt, String chibiUrl) throws IOException, InterruptedException {
        List<Map<String, Object>> parts = new ArrayList<>();
        boolean hasRef = attachReferenceImage(parts, chibiUrl);

        String fullPrompt = hasRef
                ? "Use the character in the reference image above as the exact visual style and design "
                  + "for the chibi character in this scene. Keep the same character appearance, proportions, "
                  + "and art style. Now generate: " + prompt
                : prompt;

        parts.add(Map.of("text", fullPrompt));

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of("responseModalities", List.of("IMAGE"))
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = sendWithRetry(request);

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        return extractBase64Image(response.body());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean attachReferenceImage(List<Map<String, Object>> parts, String chibiUrl) {
        if (chibiUrl == null || chibiUrl.isBlank() || !chibiUrl.startsWith("http")) {
            log.info("  No chibi URL — generating without reference image");
            return false;
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(chibiUrl)).GET().build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                String mime = resp.headers().firstValue("content-type")
                        .orElse("image/png").split(";")[0].trim();
                String b64 = Base64.getEncoder().encodeToString(resp.body());
                parts.add(Map.of("inlineData", Map.of("mimeType", mime, "data", b64)));
                log.info("  ✓ Chibi reference attached ({} bytes, {})", resp.body().length, mime);
                return true;
            }
            log.warn("  Chibi fetch returned HTTP {} — skipping", resp.statusCode());
        } catch (Exception e) {
            log.warn("  Could not fetch chibi from {} — skipping: {}", chibiUrl, e.getMessage());
        }
        return false;
    }

    private String extractBase64Image(String responseBody) throws IOException {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode candidates = root.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No candidates in Gemini response: " + responseBody);
        }

        JsonNode parts = candidates.get(0).get("content").get("parts");
        for (JsonNode part : parts) {
            JsonNode inlineData = part.get("inlineData");
            if (inlineData != null) {
                return inlineData.get("data").asText();
            }
            if (part.get("text") != null) {
                log.warn("  Gemini returned TEXT instead of image: {}",
                        part.get("text").asText().substring(0, Math.min(200, part.get("text").asText().length())));
            }
        }

        JsonNode finishReason = candidates.get(0).get("finishReason");
        throw new IOException("No image in Gemini response. finishReason=" + finishReason
                + " — check if model '" + model + "' supports image generation");
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        int maxAttempts = 3;
        IOException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    long delay = (long) Math.pow(2, attempt) * 1000L;
                    log.warn("  Gemini attempt {}/{} failed: {} — retrying in {}ms",
                            attempt, maxAttempts, e.getMessage(), delay);
                    Thread.sleep(delay);
                }
            }
        }
        throw lastError;
    }
}
