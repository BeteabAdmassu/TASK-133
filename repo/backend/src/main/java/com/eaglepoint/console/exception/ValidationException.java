package com.eaglepoint.console.exception;

import java.util.HashMap;
import java.util.Map;

public class ValidationException extends AppException {
    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(400, "VALIDATION_ERROR", message);
        this.fieldErrors = new HashMap<>();
    }

    public ValidationException(Map<String, String> fieldErrors) {
        super(400, "VALIDATION_ERROR", "Validation failed");
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String field, String message) {
        super(400, "VALIDATION_ERROR", message);
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
