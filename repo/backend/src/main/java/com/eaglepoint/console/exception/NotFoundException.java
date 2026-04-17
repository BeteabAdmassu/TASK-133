package com.eaglepoint.console.exception;

public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public NotFoundException(String entity, long id) {
        super(404, "NOT_FOUND", entity + " with id " + id + " not found");
    }
}
