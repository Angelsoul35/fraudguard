package com.angelo.fraudguard.exception;

import java.util.List;

/**
 * Thrown when an incoming request body fails validation. Carries every problem
 * found so the client gets them all at once, not one at a time.
 */
public class ValidationException extends RuntimeException {

    private final transient List<String> errors;

    public ValidationException(List<String> errors) {
        super("validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
