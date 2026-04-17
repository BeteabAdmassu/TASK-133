package com.eaglepoint.console.exception;

public class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }
}
