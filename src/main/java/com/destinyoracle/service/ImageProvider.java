package com.destinyoracle.service;

import java.io.IOException;

/**
 * Strategy interface for image generation providers.
 *
 * Each implementation handles one external AI image API (Gemini, ModelsLab, DALL-E, etc.).
 * All providers return a base64-encoded PNG string — the caller doesn't need to know
 * which API was used.
 *
 * To add a new provider:
 *   1. Create a new class implementing this interface
 *   2. Annotate with @Component("providerName")
 *   3. Add config under app.providerName.* in application.yml
 *   4. Set app.image-provider=providerName to activate it
 */
public interface ImageProvider {

    /**
     * Generate an image from a text prompt.
     *
     * @param prompt   the full image generation prompt (visual description)
     * @param chibiUrl optional URL to a reference chibi image for character consistency
     *                 (providers that don't support reference images can ignore this)
     * @return base64-encoded PNG image data
     */
    String generate(String prompt, String chibiUrl) throws IOException, InterruptedException;

    /** Human-readable name for logging (e.g. "Gemini Imagen 4", "ModelsLab animagine-xl"). */
    String providerName();
}
