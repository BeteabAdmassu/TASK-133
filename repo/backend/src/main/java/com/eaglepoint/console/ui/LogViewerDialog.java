package com.eaglepoint.console.ui;

import com.eaglepoint.console.ui.shared.ApiClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class LogViewerDialog {

    private static final Logger log = LoggerFactory.getLogger(LogViewerDialog.class);
    private static Stage stage;

    @SuppressWarnings("unchecked")
    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }

        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("System Logs");

        ObservableList<Map<String, Object>> allLogs = FXCollections.observableArrayList();
        FilteredList<Map<String, Object>> filtered = new FilteredList<>(allLogs);

        TableView<Map<String, Object>> table = new TableView<>(filtered);

        TableColumn<Map<String, Object>, String> colTs = new TableColumn<>("Timestamp");
        colTs.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("createdAt", ""))));
        colTs.setPrefWidth(160);

        TableColumn<Map<String, Object>, String> colLevel = new TableColumn<>("Level");
        colLevel.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("level", ""))));
        colLevel.setPrefWidth(70);

        TableColumn<Map<String, Object>, String> colCategory = new TableColumn<>("Category");
        colCategory.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("category", ""))));
        colCategory.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> colMessage = new TableColumn<>("Message");
        colMessage.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("message", ""))));
        colMessage.setPrefWidth(500);

        table.getColumns().addAll(colTs, colLevel, colCategory, colMessage);
        table.setPlaceholder(new Label("No logs available."));

        TextField tfSearch = new TextField();
        tfSearch.setPromptText("Search logs (Ctrl+F)...");
        tfSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                filtered.setPredicate(null);
            } else {
                String lc = newVal.toLowerCase();
                filtered.setPredicate(entry -> {
                    String msg = String.valueOf(entry.getOrDefault("message", "")).toLowerCase();
                    return msg.contains(lc);
                });
            }
        });

        Runnable loadLogs = () -> {
            try {
                Map<String, Object> resp = ApiClient.getInstance().get("/api/logs?pageSize=200");
                List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getOrDefault("data", List.of());
                Platform.runLater(() -> {
                    allLogs.setAll(data);
                });
            } catch (Exception e) {
                log.warn("Failed to load logs", e);
            }
        };

        Timeline refresh = new Timeline(new KeyFrame(Duration.seconds(10), e -> loadLogs.run()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();

        loadLogs.run();

        HBox toolbar = new HBox(8, new Label("Search:"), tfSearch);
        HBox.setHgrow(tfSearch, Priority.ALWAYS);
        VBox root = new VBox(8, toolbar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setStyle("-fx-padding: 12;");

        Scene scene = new Scene(root, 900, 600);
        scene.setOnKeyPressed(ev -> {
            if (ev.isControlDown() && ev.getCode().toString().equals("F")) {
                tfSearch.requestFocus();
            }
        });

        stage.setScene(scene);
        stage.setOnHidden(e -> refresh.stop());
        stage.show();
    }
}
