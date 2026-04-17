package com.eaglepoint.console.service;

import com.eaglepoint.console.model.AuditTrail;
import com.eaglepoint.console.repository.AuditTrailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditTrailRepository auditRepo;
    private final ObjectMapper mapper;

    public AuditService(AuditTrailRepository auditRepo) {
        this.auditRepo = auditRepo;
        this.mapper = new ObjectMapper();
    }

    public void record(String entityType, long entityId, String action, long userId,
                       String traceId, Object oldValues, Object newValues, String notes) {
        try {
            AuditTrail entry = new AuditTrail();
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setUserId(userId);
            entry.setTraceId(traceId);
            entry.setNotes(notes);

            if (oldValues != null) {
                entry.setOldValuesJson(mapper.writeValueAsString(oldValues));
            }
            if (newValues != null) {
                entry.setNewValuesJson(mapper.writeValueAsString(newValues));
            }
            auditRepo.insert(entry);
        } catch (Exception e) {
            log.error("Failed to record audit trail for {}/{}: {}", entityType, entityId, e.getMessage());
        }
    }
}
