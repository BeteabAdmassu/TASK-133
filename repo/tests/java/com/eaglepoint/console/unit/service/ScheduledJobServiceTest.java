package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.ScheduledJobConfig;
import com.eaglepoint.console.repository.ScheduledJobRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.ScheduledJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledJobServiceTest {

    @Mock private ScheduledJobRepository repo;
    @Mock private AuditService auditService;

    private ScheduledJobService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledJobService(repo, auditService);
    }

    @Test
    void createValidReportJobPersistsAndAudits() {
        ScheduledJobConfig input = new ScheduledJobConfig();
        input.setJobType("REPORT");
        input.setCronExpression("0 0 3 * * ?");
        input.setTimeoutSeconds(600);
        input.setStatus("ACTIVE");
        input.setConfigJson("{\"entityType\":\"COMMUNITIES\",\"format\":\"EXCEL\"}");

        when(repo.insert(any(ScheduledJobConfig.class))).thenReturn(42L);
        ScheduledJobConfig saved = new ScheduledJobConfig();
        saved.setId(42L);
        saved.setJobType("REPORT");
        saved.setStatus("ACTIVE");
        saved.setCronExpression("0 0 3 * * ?");
        saved.setConfigJson(input.getConfigJson());
        when(repo.findById(42L)).thenReturn(Optional.of(saved));

        ScheduledJobConfig out = service.create(input, 9L);
        assertEquals(42L, out.getId());
        verify(auditService).record(eq("ScheduledJob"), eq(42L), eq("CREATE"),
            eq(9L), any(), any(), any(), any());
    }

    @Test
    void createRejectsReportJobWithoutEntityType() {
        ScheduledJobConfig input = new ScheduledJobConfig();
        input.setJobType("REPORT");
        input.setCronExpression("0 0 3 * * ?");
        input.setConfigJson("{\"format\":\"EXCEL\"}"); // missing entityType

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.create(input, 1L));
        assertTrue(e.getMessage().contains("entityType"));
        verify(repo, never()).insert(any());
    }

    @Test
    void createRejectsReportJobWithoutAnyConfigJson() {
        ScheduledJobConfig input = new ScheduledJobConfig();
        input.setJobType("REPORT");
        input.setCronExpression("0 0 3 * * ?");

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.create(input, 1L));
        assertTrue(e.getMessage().toLowerCase().contains("configjson"));
    }

    @Test
    void createRejectsInvalidCron() {
        ScheduledJobConfig input = new ScheduledJobConfig();
        input.setJobType("BACKUP");
        input.setCronExpression("not-a-cron");

        assertThrows(ValidationException.class, () -> service.create(input, 1L));
    }

    @Test
    void createRejectsUnknownJobType() {
        ScheduledJobConfig input = new ScheduledJobConfig();
        input.setJobType("BOGUS");
        input.setCronExpression("0 0 3 * * ?");

        assertThrows(ValidationException.class, () -> service.create(input, 1L));
    }

    @Test
    void createRejectsReportWithUnknownFormat() {
        ScheduledJobConfig input = new ScheduledJobConfig();
        input.setJobType("REPORT");
        input.setCronExpression("0 0 3 * * ?");
        input.setConfigJson("{\"entityType\":\"COMMUNITIES\",\"format\":\"DOCX\"}");

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.create(input, 1L));
        assertTrue(e.getMessage().contains("format"));
    }

    @Test
    void updatePatchesFieldsAndRecordsDelta() {
        ScheduledJobConfig existing = new ScheduledJobConfig();
        existing.setId(7L);
        existing.setJobType("REPORT");
        existing.setCronExpression("0 0 3 * * ?");
        existing.setTimeoutSeconds(600);
        existing.setStatus("ACTIVE");
        existing.setConfigJson("{\"entityType\":\"COMMUNITIES\"}");
        when(repo.findById(7L)).thenReturn(Optional.of(existing));

        ScheduledJobConfig patch = new ScheduledJobConfig();
        patch.setCronExpression("0 0 5 * * ?");
        patch.setStatus("PAUSED");

        ScheduledJobConfig out = service.update(7L, patch, 3L);
        assertEquals("0 0 5 * * ?", out.getCronExpression());
        assertEquals("PAUSED", out.getStatus());
        verify(repo).update(any());
        verify(auditService).record(eq("ScheduledJob"), eq(7L), eq("UPDATE"),
            eq(3L), any(), any(), any(), any());
    }

    @Test
    void updateUnknownIdReturnsNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
            () -> service.update(99L, new ScheduledJobConfig(), 1L));
    }

    @Test
    void updateReportJobPreservesTypeAndValidatesNewConfig() {
        ScheduledJobConfig existing = new ScheduledJobConfig();
        existing.setId(10L);
        existing.setJobType("REPORT");
        existing.setCronExpression("0 0 3 * * ?");
        existing.setStatus("ACTIVE");
        existing.setConfigJson("{\"entityType\":\"COMMUNITIES\"}");
        when(repo.findById(10L)).thenReturn(Optional.of(existing));

        ScheduledJobConfig patch = new ScheduledJobConfig();
        patch.setConfigJson("{\"format\":\"PDF\"}"); // REPORT without entityType
        assertThrows(ValidationException.class,
            () -> service.update(10L, patch, 1L));
    }

    @Test
    void deleteRemovesRowAndAudits() {
        ScheduledJobConfig existing = new ScheduledJobConfig();
        existing.setId(15L);
        existing.setJobType("REPORT");
        when(repo.findById(15L)).thenReturn(Optional.of(existing));

        service.delete(15L, 77L);
        verify(repo).delete(15L);
        verify(auditService).record(eq("ScheduledJob"), eq(15L), eq("DELETE"),
            eq(77L), any(), any(), any(), any());
    }
}
