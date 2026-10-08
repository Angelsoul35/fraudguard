package com.angelo.fraudguard.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A uniform JSON error body.
 *
 * @param error   a short machine-readable error code, e.g. {@code "validation_error"}
 * @param message a human-readable summary
 * @param details optional per-field problems (omitted from JSON when empty)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String error, String message, List<String> details) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, List.of());
    }

    public static ErrorResponse of(String error, String message, List<String> details) {
        return new ErrorResponse(error, message, details);
    }
}
