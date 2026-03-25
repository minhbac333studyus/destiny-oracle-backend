package com.destinyoracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard envelope for every API response.
 *
 * List endpoints automatically populate {@code count} with the size of the returned list.
 * Single-object and void endpoints leave {@code count} null (omitted from JSON).
 *
 * Uses manual constructors — Lombok @Builder doesn't support generic type inference
 * on static factory methods.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Whether the request succeeded", example = "true")
    private final boolean success;

    @Schema(description = "Human-readable result message", example = "OK")
    private final String message;

    @Schema(description = "Number of items returned — only present on list endpoints")
    private final Integer count;

    @Schema(description = "Response payload")
    private final T data;

    @Schema(description = "Server timestamp of the response")
    private final LocalDateTime timestamp;

    // ── Constructors ───────────────────────────────────────────────────────────

    private ApiResponse(boolean success, String message, Integer count, T data) {
        this.success   = success;
        this.message   = message;
        this.count     = count;
        this.data      = data;
        this.timestamp = LocalDateTime.now();
    }

    // ── Factory methods ────────────────────────────────────────────────────────

    /** Single object or void response — no count. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", null, data);
    }

    /** List response — count is automatically set to list size. */
    public static <T> ApiResponse<List<T>> success(List<T> data) {
        return new ApiResponse<>(true, "OK", data == null ? 0 : data.size(), data);
    }

    /** Single object with a custom message. */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, null, data);
    }

    /** List response with a custom message — count auto-set. */
    public static <T> ApiResponse<List<T>> success(String message, List<T> data) {
        return new ApiResponse<>(true, message, data == null ? 0 : data.size(), data);
    }

    /** Void response (DELETE, PUT with no body) — no data, no count. */
    public static ApiResponse<Void> successVoid() {
        return new ApiResponse<>(true, "OK", null, null);
    }

    /** Error response — no data, no count. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }
}
