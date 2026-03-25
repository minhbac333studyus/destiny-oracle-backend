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
 * ModelsLab (Stable Diffusion) image generation provider.
 * Supports anime-optimized models like animagine-xl, counterfeit-v3, anything-v5.
 */
@Slf4j
@Component("modelslab")
public class ModelsLabImageProvider implements ImageProvider {

    @Value("${app.modelslab.api-key:}")
    private String apiKey;

    @Value("${app.modelslab.model-id:}")
    private String modelId;

    @Value("${app.modelslab.base-url:https://modelslab.com/api/v6/images/text2img}")
    private String baseUrl;

    @Value("${app.modelslab.width:512}")
    private int width;

    @Value("${app.modelslab.height:912}")
    private int height;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String providerName() {
        return "ModelsLab " + modelId;
    }

    @Override
    public String generate(String prompt, String chibiUrl) throws IOException, InterruptedException {
        // chibiUrl is ignored — ModelsLab text2img doesn't support reference images
        // (img2img endpoint would, but that's a different API)

        String enhancedPrompt = prompt
                + ", anime cel-shading style, chibi 2.5-head proportion, large sparkling eyes, "
                + "soft rounded features, Studio Ghibli inspired lighting";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", apiKey);
        body.put("model_id", modelId);
        body.put("prompt", enhancedPrompt);
        body.put("negative_prompt",
                "text, watermark, blurry, realistic, photograph, 3d render, deformed, "
                + "bad anatomy, extra limbs, ugly, low quality, words, letters, signature");
        body.put("width", String.valueOf(width));
        body.put("height", String.valueOf(height));
        body.put("samples", "1");
        body.put("num_inference_steps", "30");
        body.put("guidance_scale", 7.5);
        body.put("base64", "yes");
        body.put("strength",1);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        // Retry loop with exponential backoff for rate limits.
        // Fires immediately — if rate limited, backs off: 5s → 10s → 20s → 40s → 80s
        int maxRetries = 6;
        String bodyJson = mapper.writeValueAsString(body);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() != 200) {
                throw new IOException("ModelsLab API error " + response.statusCode() + ": " + response.body());
            }

            String result = parseResponse(response.body(), attempt < maxRetries);

            if (result != null) {
                return result;
            }

            // Rate limited — exponential backoff: 5s, 10s, 20s, 40s, 80s
            long waitMs = 5000L * (long) Math.pow(2, attempt - 1);
            log.warn("  ⏳ Rate limited — backoff {}s, retry {}/{}", waitMs / 1000, attempt, maxRetries);
            Thread.sleep(waitMs);
        }

        throw new IOException("ModelsLab rate limit not resolved after " + maxRetries + " retries");
    }

    // ── Response parsing ────────────────────────────────────────────────────

    /**
     * @return base64 image on success, null if rate limited and shouldRetry is true
     */
    private String parseResponse(String responseBody, boolean shouldRetry) throws IOException, InterruptedException {
        JsonNode root = mapper.readTree(responseBody);
        String status = root.has("status") ? root.get("status").asText() : "";

        if ("success".equals(status)) {
            return extractImageData(root);
        } else if ("processing".equals(status)) {
            String fetchUrl = root.has("fetch_result") ? root.get("fetch_result").asText() : null;
            if (fetchUrl != null) {
                return pollForResult(fetchUrl);
            }
        } else if ("error".equals(status)) {
            String message = root.has("message") ? root.get("message").asText() : "";
            if (message.toLowerCase().contains("rate limit") && shouldRetry) {
                return null; // signal caller to retry
            }
        }

        throw new IOException("ModelsLab generation failed: " + responseBody);
    }

    private String extractImageData(JsonNode root) throws IOException, InterruptedException {
        JsonNode output = root.get("output");
        if (output == null || !output.isArray() || output.isEmpty()) {
            throw new IOException("ModelsLab returned success but no output array");
        }

        String data = output.get(0).asText();
        if (data.startsWith("http")) {
            return fetchImageAsBase64(data);
        }
        // Strip data URI prefix if present (e.g. "data:image/png;base64,...")
        if (data.contains(",")) {
            data = data.substring(data.indexOf(",") + 1);
        }
        return data;
    }

    private String pollForResult(String fetchUrl) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < 30; i++) { // max 60s
            Thread.sleep(2000);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(fetchUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"key\": \"" + apiKey + "\"}"))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(resp.body());
            String status = root.has("status") ? root.get("status").asText() : "";

            if ("success".equals(status)) {
                return extractImageData(root);
            } else if ("failed".equals(status) || "error".equals(status)) {
                throw new IOException("ModelsLab generation failed: " + resp.body());
            }
            log.debug("  ModelsLab poll {}/30 — status: {}", i + 1, status);
        }
        throw new IOException("ModelsLab generation timed out after 60s");
    }

    private String fetchImageAsBase64(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

        String contentType = resp.headers().firstValue("content-type").orElse("unknown");
        log.info("  Fetched image from URL: {} bytes, content-type={}, status={}",
                resp.body().length, contentType, resp.statusCode());

        if (resp.statusCode() != 200) {
            throw new IOException("Failed to fetch ModelsLab image: HTTP " + resp.statusCode());
        }

        byte[] bytes = resp.body();

        // Validate by checking magic bytes — CDN sometimes returns text/plain for valid images
        if (isImageBytes(bytes)) {
            log.info("  ✓ Valid image data ({} KB)", bytes.length / 1024);
            return Base64.getEncoder().encodeToString(bytes);
        }

        // Maybe the response body IS base64 text (not raw bytes)
        String bodyStr = new String(bytes).trim();
        if (bodyStr.startsWith("/9j/") || bodyStr.startsWith("iVBOR")) {
            log.info("  ✓ Response is base64 text ({} chars), returning as-is", bodyStr.length());
            // Strip data URI prefix if present
            if (bodyStr.contains(",")) {
                bodyStr = bodyStr.substring(bodyStr.indexOf(",") + 1);
            }
            return bodyStr;
        }

        String preview = new String(bytes, 0, Math.min(200, bytes.length));
        log.error("  ModelsLab returned unrecognized content: {}", preview);
        throw new IOException("ModelsLab URL returned unrecognized content (not image bytes or base64)");
    }

    /** Check magic bytes to determine if data is a real image regardless of content-type header. */
    private boolean isImageBytes(byte[] data) {
        if (data.length < 4) return false;
        // JPEG: FF D8 FF
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) return true;
        // PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        // WebP: RIFF....WEBP
        if (data.length > 11 && data[0] == 0x52 && data[1] == 0x49 && data[8] == 0x57 && data[9] == 0x45) return true;
        return false;
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
                    log.warn("  ModelsLab attempt {}/{} failed: {} — retrying in {}ms",
                            attempt, maxAttempts, e.getMessage(), delay);
                    Thread.sleep(delay);
                }
            }
        }
        throw lastError;
    }
}
