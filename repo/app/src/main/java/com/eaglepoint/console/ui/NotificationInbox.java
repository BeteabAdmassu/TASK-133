package com.eaglepoint.console.ui;

import com.eaglepoint.console.service.NotificationService;
import com.eaglepoint.console.service.NotificationService.Notification;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.util.List;

public class NotificationInbox {

    private final Button badgeButton;
    private final NotificationService notificationService;
    private Popup popup;

    public NotificationInbox(Button badgeButton) {
        this.badgeButton = badgeButton;
        this.notificationService = NotificationService.getInstance();
        notificationService.subscribe(n -> Platform.runLater(this::refreshBadge));
    }

    public void refreshBadge() {
        List<Notification> undismissed = notificationService.getUndismissed();
        int count = undismissed.size();
        badgeButton.setText("Inbox (" + count + ")");
        if (count > 0) {
            badgeButton.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            badgeButton.setStyle("");
        }
    }

    public void toggle(Stage owner) {
        if (popup != null && popup.isShowing()) {
            popup.hide();
            popup = null;
            return;
        }
        popup = buildPopup(owner);
        popup.show(owner);
    }

    private Popup buildPopup(Stage owner) {
        Popup p = new Popup();
        VBox panel = new VBox(4);
        panel.getStyleClass().add("notification-panel");
        panel.setMinWidth(320);
        panel.setMaxWidth(360);

        Label title = new Label("Notifications");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        panel.getChildren().add(title);

        List<Notification> items = notificationService.getUndismissed();
        if (items.isEmpty()) {
            panel.getChildren().add(new Label("No new notifications."));
        } else {
            ScrollPane scroll = new ScrollPane();
            VBox itemList = new VBox(2);
            for (int i = 0; i < items.size(); i++) {
                Notification n = items.get(i);
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                String styleClass = switch (n.getSeverity()) {
                    case "ERROR" -> "notification-item-error";
                    case "WARN" -> "notification-item-warn";
                    default -> "notification-item-info";
                };
                row.getStyleClass().addAll("notification-item", styleClass);
                Label msg = new Label(n.getMessage());
                msg.setWrapText(true);
                HBox.setHgrow(msg, Priority.ALWAYS);
                final int idx = i;
                Button dismiss = new Button("×");
                dismiss.setOnAction(e -> {
                    notificationService.dismiss(idx);
                    refreshBadge();
                    p.hide();
                    popup = null;
                });
                row.getChildren().addAll(msg, dismiss);
                itemList.getChildren().add(row);
            }
            scroll.setContent(itemList);
            scroll.setMaxHeight(400);
            panel.getChildren().add(scroll);
        }

        panel.getStylesheets().add(
            getClass().getResource("/css/application.css").toExternalForm());
        p.getContent().add(panel);
        p.setAutoHide(true);
        return p;
    }
}
