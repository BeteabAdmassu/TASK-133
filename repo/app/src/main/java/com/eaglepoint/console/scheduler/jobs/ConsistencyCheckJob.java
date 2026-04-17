package com.eaglepoint.console.scheduler.jobs;

import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.service.ConsistencyService;
import com.eaglepoint.console.service.NotificationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConsistencyCheckJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.info("ConsistencyCheckJob starting");
        ConsistencyService service = new ConsistencyService(DatabaseConfig.getInstance().getDataSource());
        try {
            List<String> issues = service.runChecks();
            if (!issues.isEmpty()) {
                log.warn("Consistency check found {} issues", issues.size());
                issues.forEach(issue -> log.warn("  - {}", issue));
                NotificationService.getInstance().addAlert("WARN",
                    "Consistency check found " + issues.size() + " issue(s). Check system logs for details.",
                    "ConsistencyCheck", null);
            } else {
                log.info("Consistency check passed - no issues found");
            }
        } catch (Exception e) {
            log.error("ConsistencyCheckJob failed: {}", e.getMessage(), e);
            NotificationService.getInstance().addAlert("ERROR",
                "Consistency check failed: " + e.getMessage(), "ConsistencyCheck", null);
        }
    }
}
