package com.eaglepoint.console.service;

import com.eaglepoint.console.model.SystemLog;
import com.eaglepoint.console.repository.SystemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central write path for persisted system and business events.
 * Wraps {@link SystemLogRepository#insert} so callers don't need to build
 * {@link SystemLog} objects directly.  Every key workflow transition (pickup
 * point lifecycle, auth events, override actions, etc.) should call this
 * service so that {@code GET /api/logs} returns a meaningful audit stream.
 */
public class SystemLogService {
    private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);
    private final SystemLogRepository logRepo;

    public SystemLogService(SystemLogRepository logRepo) {
        this.logRepo = logRepo;
    }

    public void info(String category, String message, String entityType, Long entityId, Long userId) {
        persist("INFO", category, message, entityType, entityId, userId, null, null);
    }

    public void warn(String category, String message, String entityType, Long entityId, Long userId) {
        persist("WARN", category, message, entityType, entityId, userId, null, null);
    }

    public void error(String category, String message, String entityType, Long entityId, Long userId) {
        persist("ERROR", category, message, entityType, entityId, userId, null, null);
    }

    public void logRequest(String path, int statusCode, int durationMs, Long userId, String requestId) {
        persist("INFO", "SYSTEM", "HTTP " + statusCode + " " + path,
            null, null, userId, path, statusCode, durationMs, requestId);
    }

    private void persist(String level, String category, String message,
                         String entityType, Long entityId, Long userId,
                         String path, Integer statusCode) {
        persist(level, category, message, entityType, entityId, userId, path, statusCode, null, null);
    }

    private void persist(String level, String category, String message,
                         String entityType, Long entityId, Long userId,
                         String path, Integer statusCode, Integer durationMs, String requestId) {
        try {
            SystemLog entry = new SystemLog();
            entry.setLevel(level);
            entry.setCategory(category);
            entry.setMessage(message);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setUserId(userId);
            entry.setPath(path);
            entry.setStatusCode(statusCode);
            entry.setDurationMs(durationMs);
            entry.setRequestId(requestId);
            logRepo.insert(entry);
        } catch (Exception e) {
            log.error("Failed to persist system log entry: {}", e.getMessage());
        }
    }
}
