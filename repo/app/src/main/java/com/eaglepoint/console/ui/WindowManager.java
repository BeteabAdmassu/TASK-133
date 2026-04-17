package com.eaglepoint.console.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WindowManager {

    private static final Logger log = LoggerFactory.getLogger(WindowManager.class);
    private static WindowManager instance;

    private final Map<Class<?>, Stage> openWindows = new ConcurrentHashMap<>();

    private WindowManager() {}

    public static synchronized WindowManager getInstance() {
        if (instance == null) {
            instance = new WindowManager();
        }
        return instance;
    }

    public void register(Class<?> windowClass, Stage stage) {
        openWindows.put(windowClass, stage);
        stage.setOnHidden(e -> openWindows.remove(windowClass));
    }

    public void open(Class<?> windowClass) {
        Platform.runLater(() -> {
            Stage existing = openWindows.get(windowClass);
            if (existing != null && existing.isShowing()) {
                existing.toFront();
                existing.requestFocus();
            } else {
                log.debug("Window not registered or not open: {}", windowClass.getSimpleName());
            }
        });
    }

    public void bringToFront(Stage stage) {
        Platform.runLater(() -> {
            if (stage != null && stage.isShowing()) {
                stage.toFront();
            }
        });
    }

    public void closeAll() {
        Platform.runLater(() -> {
            openWindows.values().forEach(stage -> {
                if (stage.isShowing()) {
                    stage.close();
                }
            });
            openWindows.clear();
        });
    }

    public boolean isOpen(Class<?> windowClass) {
        Stage stage = openWindows.get(windowClass);
        return stage != null && stage.isShowing();
    }
}
