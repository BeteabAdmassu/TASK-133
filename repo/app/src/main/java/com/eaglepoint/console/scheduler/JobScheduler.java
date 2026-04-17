package com.eaglepoint.console.scheduler;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.model.ScheduledJobConfig;
import com.eaglepoint.console.repository.ScheduledJobRepository;
import com.eaglepoint.console.scheduler.jobs.ArchivalJob;
import com.eaglepoint.console.scheduler.jobs.BackupJob;
import com.eaglepoint.console.scheduler.jobs.ConsistencyCheckJob;
import com.eaglepoint.console.service.PickupPointService;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JobScheduler {
    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
    private static JobScheduler instance;

    private Scheduler quartzScheduler;
    private ScheduledExecutorService minuteExecutor;
    private ScheduledJobRepository jobRepo;
    private PickupPointService pickupPointService;

    // Static service references for jobs to access
    private static Object[] serviceContext;

    public static synchronized JobScheduler getInstance() {
        if (instance == null) instance = new JobScheduler();
        return instance;
    }

    public void init(ScheduledJobRepository jobRepo, PickupPointService pickupPointService,
                     Object... services) {
        this.jobRepo = jobRepo;
        this.pickupPointService = pickupPointService;
        serviceContext = services;
    }

    public static Object[] getServiceContext() {
        return serviceContext;
    }

    public void start() throws Exception {
        if (quartzScheduler != null && quartzScheduler.isStarted()) {
            return; // idempotent — tests and multiple entry points may call start() more than once
        }
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "ConsoleScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "3");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        quartzScheduler = new StdSchedulerFactory(props).getScheduler();

        // Register jobs from database
        List<ScheduledJobConfig> jobs = jobRepo.findAll();
        for (ScheduledJobConfig job : jobs) {
            if ("ACTIVE".equals(job.getStatus())) {
                registerJob(job);
            }
        }

        quartzScheduler.start();
        log.info("Quartz scheduler started with {} jobs", jobs.size());

        // Start minute-level executor for pickup point pause resume
        minuteExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pickup-resume-checker");
            t.setDaemon(true);
            return t;
        });
        minuteExecutor.scheduleAtFixedRate(this::checkExpiredPauses, 60, 60, TimeUnit.SECONDS);
    }

    private void registerJob(ScheduledJobConfig config) {
        try {
            Class<? extends Job> jobClass = switch (config.getJobType()) {
                case "BACKUP" -> BackupJob.class;
                case "ARCHIVE" -> ArchivalJob.class;
                case "CONSISTENCY_CHECK" -> ConsistencyCheckJob.class;
                default -> null;
            };
            if (jobClass == null) return;

            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(config.getJobType() + "-" + config.getId())
                .usingJobData("dbJobId", config.getId())
                .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(config.getJobType() + "-trigger-" + config.getId())
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getCronExpression()))
                .build();

            quartzScheduler.scheduleJob(jobDetail, trigger);
            log.info("Registered job: {} with cron: {}", config.getJobType(), config.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to register job {}: {}", config.getJobType(), e.getMessage());
        }
    }

    private void checkExpiredPauses() {
        try {
            if (pickupPointService != null) {
                pickupPointService.resumeExpiredPauses();
            }
        } catch (Exception e) {
            log.error("Error checking expired pauses: {}", e.getMessage());
        }
    }

    public void pauseJob(long dbJobId) throws Exception {
        String jobKey = findJobKey(dbJobId);
        if (jobKey != null) quartzScheduler.pauseJob(JobKey.jobKey(jobKey));
        jobRepo.pauseJob(dbJobId);
    }

    public void resumeJob(long dbJobId) throws Exception {
        String jobKey = findJobKey(dbJobId);
        if (jobKey != null) quartzScheduler.resumeJob(JobKey.jobKey(jobKey));
        jobRepo.resumeJob(dbJobId);
    }

    private String findJobKey(long dbJobId) {
        ScheduledJobConfig config = jobRepo.findById(dbJobId).orElse(null);
        if (config == null) return null;
        return config.getJobType() + "-" + dbJobId;
    }

    public void shutdown() {
        if (minuteExecutor != null) {
            minuteExecutor.shutdownNow();
        }
        if (quartzScheduler != null) {
            try {
                quartzScheduler.shutdown(false);
                log.info("Quartz scheduler stopped");
            } catch (Exception e) {
                log.error("Error stopping scheduler: {}", e.getMessage());
            }
        }
    }

    public String getStartTime() {
        return Instant.now().toString();
    }
}
