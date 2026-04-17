package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.model.ExportJob;
import com.eaglepoint.console.scheduler.JobScheduler;
import com.eaglepoint.console.scheduler.jobs.ScheduledReportJob;
import com.eaglepoint.console.service.ExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that ScheduledReportJob goes through the real ExportService
 * pipeline (success) and surfaces failures without crashing Quartz.
 */
class ScheduledReportJobTest {

    @AfterEach
    void tearDown() throws Exception {
        // Reset the static ExportService binding after each test.
        Field f = JobScheduler.class.getDeclaredField("exportService");
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    void executeCallsExportServiceWithParsedConfig() throws Exception {
        ExportService exportService = mock(ExportService.class);
        bindExportService(exportService);

        ExportJob fake = new ExportJob();
        fake.setId(123L);
        fake.setType("EXCEL");
        fake.setEntityType("COMMUNITIES");
        when(exportService.createExportJob(anyString(), anyString(), anyString(), any(), anyLong()))
            .thenReturn(fake);

        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDetail detail = mock(JobDetail.class);
        JobDataMap data = new JobDataMap();
        data.put("configJson",
            "{\"entityType\":\"COMMUNITIES\",\"format\":\"EXCEL\",\"destinationPath\":\"/tmp/reports\"}");
        when(ctx.getJobDetail()).thenReturn(detail);
        when(detail.getJobDataMap()).thenReturn(data);

        new ScheduledReportJob().execute(ctx);

        verify(exportService).createExportJob(
            eq("EXCEL"), eq("COMMUNITIES"), eq("/tmp/reports"), isNull(), anyLong());
    }

    @Test
    void executeLogsErrorIfExportServiceNotBound() throws Exception {
        // No binding — the job must degrade gracefully, not throw.
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDetail detail = mock(JobDetail.class);
        JobDataMap data = new JobDataMap();
        when(ctx.getJobDetail()).thenReturn(detail);
        when(detail.getJobDataMap()).thenReturn(data);

        assertDoesNotThrow(() -> new ScheduledReportJob().execute(ctx));
    }

    @Test
    void executeFailsGracefullyOnBadConfig() throws Exception {
        ExportService exportService = mock(ExportService.class);
        bindExportService(exportService);

        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDetail detail = mock(JobDetail.class);
        JobDataMap data = new JobDataMap();
        // Missing required entityType
        data.put("configJson", "{\"format\":\"EXCEL\"}");
        when(ctx.getJobDetail()).thenReturn(detail);
        when(detail.getJobDataMap()).thenReturn(data);

        assertDoesNotThrow(() -> new ScheduledReportJob().execute(ctx));
        verify(exportService, never()).createExportJob(any(), any(), any(), any(), anyLong());
    }

    private void bindExportService(ExportService exportService) throws Exception {
        Field f = JobScheduler.class.getDeclaredField("exportService");
        f.setAccessible(true);
        f.set(null, exportService);
    }
}
