package com.eaglepoint.console.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.ConsoleAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import org.slf4j.LoggerFactory;

import java.io.File;

public class LoggingConfig {
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        AppConfig config = AppConfig.getInstance();
        String logDir = config.getLogDir();
        new File(logDir).mkdirs();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        // Business log appender
        RollingFileAppender<ILoggingEvent> businessAppender = createRollingAppender(
            context, "BUSINESS", logDir + "/business.log", logDir + "/business.%d{yyyy-MM-dd}.log"
        );

        // System log appender
        RollingFileAppender<ILoggingEvent> systemAppender = createRollingAppender(
            context, "SYSTEM", logDir + "/system.log", logDir + "/system.%d{yyyy-MM-dd}.log"
        );

        // Root logger
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(systemAppender);

        // Business category logger
        Logger businessLogger = context.getLogger("BUSINESS");
        businessLogger.setLevel(Level.INFO);
        businessLogger.addAppender(businessAppender);
        businessLogger.setAdditive(false);

        // Console appender for headless/dev mode
        if (config.isHeadless() || "true".equals(System.getProperty("logback.console"))) {
            ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
            consoleAppender.setContext(context);
            consoleAppender.setName("CONSOLE");
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.start();
            consoleAppender.setEncoder(encoder);
            consoleAppender.start();
            rootLogger.addAppender(consoleAppender);
        }
    }

    private static RollingFileAppender<ILoggingEvent> createRollingAppender(
            LoggerContext context, String name, String file, String filePattern) {
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setName(name);
        appender.setFile(file);

        TimeBasedRollingPolicy<ILoggingEvent> policy = new TimeBasedRollingPolicy<>();
        policy.setContext(context);
        policy.setParent(appender);
        policy.setFileNamePattern(filePattern);
        policy.setMaxHistory(90);
        policy.start();
        appender.setRollingPolicy(policy);

        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.start();
        appender.setEncoder(encoder);

        appender.start();
        return appender;
    }
}
