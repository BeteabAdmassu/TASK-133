package com.eaglepoint.console.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class InactivityMonitor {

    private static final Logger log = LoggerFactory.getLogger(InactivityMonitor.class);
    private static final int TIMEOUT_SECONDS = 600; // 10 minutes
    private static InactivityMonitor instance;

    private final AtomicInteger secondsIdle = new AtomicInteger(0);
    private Timeline ticker;
    private Runnable onTimeout;

    private InactivityMonitor() {}

    public static synchronized InactivityMonitor getInstance() {
        if (instance == null) {
            instance = new InactivityMonitor();
        }
        return instance;
    }

    public void start(Runnable onTimeout) {
        this.onTimeout = onTimeout;
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
    }

    public void stop() {
        if (ticker != null) {
            ticker.stop();
        }
    }

    public void resetTimer() {
        secondsIdle.set(0);
    }

    private void tick() {
        int idle = secondsIdle.incrementAndGet();
        if (idle >= TIMEOUT_SECONDS) {
            secondsIdle.set(0);
            log.info("Inactivity timeout reached — locking session");
            if (onTimeout != null) {
                onTimeout.run();
            }
        }
    }

    public void installOnScene(Scene scene) {
        scene.setOnMouseMoved(e -> resetTimer());
        scene.setOnMouseClicked(e -> resetTimer());
        scene.setOnKeyPressed(e -> resetTimer());
        scene.setOnKeyReleased(e -> resetTimer());
    }
}
