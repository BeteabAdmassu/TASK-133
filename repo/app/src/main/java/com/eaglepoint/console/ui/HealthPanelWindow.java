package com.eaglepoint.console.ui;

import com.eaglepoint.console.ui.shared.ApiClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class HealthPanelWindow {

    private static final Logger log = LoggerFactory.getLogger(HealthPanelWindow.class);
    private static Stage stage;

    @SuppressWarnings("unchecked")
    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }

        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Health Panel");

        Label lblStatus = new Label("Status: ...");
        Label lblUptime = new Label("Uptime: ...");
        Label lblDb = new Label("DB: ...");

        GridPane statusGrid = new GridPane();
        statusGrid.setHgap(16);
        statusGrid.setVgap(8);
        statusGrid.setPadding(new Insets(12));
        statusGrid.add(new Label("Status:"), 0, 0);
        statusGrid.add(lblStatus, 1, 0);
        statusGrid.add(new Label("Uptime:"), 0, 1);
        statusGrid.add(lblUptime, 1, 1);
        statusGrid.add(new Label("Database:"), 0, 2);
        statusGrid.add(lblDb, 1, 2);

        TableView<Map<String, Object>> jobTable = new TableView<>();
        TableColumn<Map<String, Object>, String> colName = new TableColumn<>("Job");
        colName.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("jobType", ""))));
        colName.setPrefWidth(150);

        TableColumn<Map<String, Object>, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("status", ""))));
        colStatus.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> colLastRun = new TableColumn<>("Last Run");
        colLastRun.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("lastRun", ""))));
        colLastRun.setPrefWidth(160);

        TableColumn<Map<String, Object>, String> colResult = new TableColumn<>("Last Result");
        colResult.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("lastResult", ""))));
        colResult.setPrefWidth(200);

        jobTable.getColumns().addAll(colName, colStatus, colLastRun, colResult);
        jobTable.setPlaceholder(new Label("No scheduled jobs."));

        Button btnPause = new Button("Pause Job");
        btnPause.setDisable(true);
        Button btnResume = new Button("Resume Job");
        btnResume.setDisable(true);

        jobTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            btnPause.setDisable(sel == null);
            btnResume.setDisable(sel == null);
        });

        btnPause.setOnAction(e -> {
            Map<String, Object> sel = jobTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                long id = ((Number) sel.get("id")).longValue();
                new Thread(() -> {
                    try {
                        ApiClient.getInstance().post("/api/jobs/" + id + "/pause", Map.of());
                    } catch (Exception ex) {
                        log.warn("Failed to pause job", ex);
                    }
                }).start();
            }
        });

        btnResume.setOnAction(e -> {
            Map<String, Object> sel = jobTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                long id = ((Number) sel.get("id")).longValue();
                new Thread(() -> {
                    try {
                        ApiClient.getInstance().post("/api/jobs/" + id + "/resume", Map.of());
                    } catch (Exception ex) {
                        log.warn("Failed to resume job", ex);
                    }
                }).start();
            }
        });

        Runnable loadData = () -> {
            try {
                Map<String, Object> health = ApiClient.getInstance().get("/api/health");
                Map<String, Object> jobs = ApiClient.getInstance().get("/api/jobs?pageSize=50");
                List<Map<String, Object>> jobList = (List<Map<String, Object>>) jobs.getOrDefault("data", List.of());
                Platform.runLater(() -> {
                    lblStatus.setText(String.valueOf(health.getOrDefault("status", "?")));
                    long uptimeMs = ((Number) health.getOrDefault("uptime", 0)).longValue();
                    long seconds = uptimeMs / 1000;
                    lblUptime.setText(String.format("%d h %d m %d s",
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60));
                    lblDb.setText(String.valueOf(health.getOrDefault("db", "?")));
                    jobTable.setItems(FXCollections.observableArrayList(jobList));
                });
            } catch (Exception e) {
                log.warn("Failed to load health data", e);
            }
        };

        Timeline refreshTl = new Timeline(new KeyFrame(Duration.seconds(10), e -> loadData.run()));
        refreshTl.setCycleCount(Timeline.INDEFINITE);
        refreshTl.play();
        loadData.run();

        HBox toolbar = new HBox(8, btnPause, btnResume);
        toolbar.setPadding(new Insets(8));

        VBox root = new VBox(12, statusGrid, new Label("Scheduled Jobs:"), jobTable, toolbar);
        VBox.setVgrow(jobTable, javafx.scene.layout.Priority.ALWAYS);
        root.setPadding(new Insets(12));

        stage.setScene(new Scene(root, 750, 500));
        stage.setOnHidden(e -> refreshTl.stop());
        stage.show();
    }
}
