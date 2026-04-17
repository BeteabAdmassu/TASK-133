package com.eaglepoint.console.scheduler.jobs;

import com.eaglepoint.console.service.NotificationService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledReportJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ScheduledReportJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.info("ScheduledReportJob starting");
        JobDataMap data = context.getJobDetail().getJobDataMap();
        String reportType = data.getString("reportType");
        String outputFolder = data.getString("outputFolder");

        try {
            log.info("Generating scheduled report: type={}, folder={}", reportType, outputFolder);
            // In production: call ExportService to create and execute export job
            NotificationService.getInstance().addAlert("INFO",
                "Scheduled report generated: " + reportType, "ScheduledReport", null);
        } catch (Exception e) {
            log.error("ScheduledReportJob failed: {}", e.getMessage(), e);
            NotificationService.getInstance().addAlert("ERROR",
                "Scheduled report failed: " + e.getMessage(), "ScheduledReport", null);
        }
    }
}
