package com.eaglepoint.console;

import com.eaglepoint.console.api.ApiServer;
import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.config.LoggingConfig;
import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.scheduler.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeadlessEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(HeadlessEntryPoint.class);

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        LoggingConfig.init();
        AppConfig.init();
        SecurityConfig.initHeadless();
        DatabaseConfig.init();

        int port = AppConfig.getInstance().getApiPort();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered — stopping services");
            try {
                ApiServer.stop();
                JobScheduler.getInstance().shutdown();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }, "shutdown-hook"));

        ApiServer.start(port);
        JobScheduler.getInstance().start();

        log.info("Eagle Point Console (headless) started on port {}", port);

        // Block until JVM exit
        Thread.currentThread().join();
    }
}
