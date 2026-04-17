package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.RouteImport;
import com.eaglepoint.console.repository.RouteImportRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.NotificationService;
import com.eaglepoint.console.service.RouteImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteImportServiceTest {

    @Mock private RouteImportRepository routeImportRepo;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private RouteImportService service;

    @BeforeEach
    void setUp() {
        service = new RouteImportService(routeImportRepo, notificationService, auditService);
    }

    @Test
    void startImportSucceedsWithValidCsv() throws Exception {
        String csv = "lat,lon,timestamp,sequence\n37.7749,-122.4194,2024-01-01T10:00:00Z,1\n";
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        RouteImport importObj = new RouteImport();
        importObj.setId(1L);

        when(routeImportRepo.insert(any(RouteImport.class))).thenReturn(1L);
        when(routeImportRepo.findById(1L)).thenReturn(Optional.of(importObj));

        RouteImport result = service.startImport("route.csv", content, 1L);
        assertNotNull(result);
    }

    @Test
    void startImportFailsForEmptyFile() {
        byte[] empty = new byte[0];
        assertThrows(ValidationException.class, () ->
            service.startImport("empty.csv", empty, 1L));
    }

    @Test
    void startImportFailsForUnsupportedExtension() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        assertThrows(ValidationException.class, () ->
            service.startImport("route.xlsx", content, 1L));
    }

    @Test
    void getImportReturnsExistingImport() {
        RouteImport importObj = new RouteImport();
        importObj.setId(5L);
        when(routeImportRepo.findById(5L)).thenReturn(Optional.of(importObj));

        RouteImport result = service.getImport(5L);
        assertEquals(5L, result.getId());
    }

    @Test
    void getImportThrowsForMissingId() {
        when(routeImportRepo.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(com.eaglepoint.console.exception.NotFoundException.class, () ->
            service.getImport(999L));
    }
}
