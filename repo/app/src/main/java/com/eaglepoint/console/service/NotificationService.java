package com.eaglepoint.console.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class NotificationService {
    private static NotificationService instance;

    public static class Notification {
        private final String severity;
        private final String message;
        private final String entityType;
        private final Long entityId;
        private final String timestamp;
        private boolean dismissed;

        public Notification(String severity, String message, String entityType, Long entityId) {
            this.severity = severity;
            this.message = message;
            this.entityType = entityType;
            this.entityId = entityId;
            this.timestamp = Instant.now().toString();
            this.dismissed = false;
        }

        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getEntityType() { return entityType; }
        public Long getEntityId() { return entityId; }
        public String getTimestamp() { return timestamp; }
        public boolean isDismissed() { return dismissed; }
        public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }
    }

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();
    private final List<Consumer<Notification>> listeners = new CopyOnWriteArrayList<>();

    public static synchronized NotificationService getInstance() {
        if (instance == null) instance = new NotificationService();
        return instance;
    }

    public void addAlert(Notification notification) {
        notifications.add(notification);
        listeners.forEach(l -> {
            try { l.accept(notification); } catch (Exception ignored) {}
        });
    }

    public void addAlert(String severity, String message, String entityType, Long entityId) {
        addAlert(new Notification(severity, message, entityType, entityId));
    }

    public List<Notification> getUndismissed() {
        List<Notification> result = new ArrayList<>();
        for (Notification n : notifications) {
            if (!n.isDismissed()) result.add(n);
        }
        return Collections.unmodifiableList(result);
    }

    public void dismiss(int index) {
        List<Notification> undismissed = new ArrayList<>();
        for (Notification n : notifications) {
            if (!n.isDismissed()) undismissed.add(n);
        }
        if (index >= 0 && index < undismissed.size()) {
            undismissed.get(index).setDismissed(true);
        }
    }

    public void subscribe(Consumer<Notification> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Notification> listener) {
        listeners.remove(listener);
    }
}
