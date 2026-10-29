package com.example.inventory.exception;

import java.util.List;

/** Thrown when input fails validation. Carries every problem found, not just the first. */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // always an immutable list of Strings
    private final List<String> errors;

    public ValidationException(String error) {
        this(List.of(error));
    }

    public ValidationException(List<String> errors) {
        super("Validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
