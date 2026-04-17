package com.eaglepoint.console.ui.shared;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExportDialog {

    private static final Logger log = LoggerFactory.getLogger(ExportDialog.class);

    public static void show(String entityType, Stage owner) {
        Platform.runLater(() -> {
            Stage dialog = new Stage();
            dialog.initOwner(owner);
            dialog.setTitle("Export — " + entityType);

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(12);
            grid.setPadding(new Insets(16));

            ComboBox<String> cbFormat = new ComboBox<>();
            cbFormat.getItems().addAll("EXCEL", "PDF", "CSV");
            cbFormat.setValue("EXCEL");

            TextField tfDestination = new TextField();
            tfDestination.setPromptText("Output folder path");
            tfDestination.setPrefWidth(300);

            Button btnBrowse = new Button("Browse...");
            btnBrowse.setOnAction(e -> {
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle("Select Output Folder");
                File dir = dc.showDialog(dialog);
                if (dir != null) tfDestination.setText(dir.getAbsolutePath());
            });

            grid.add(new Label("Format:"), 0, 0);
            grid.add(cbFormat, 1, 0);
            grid.add(new Label("Destination:"), 0, 1);
            HBox destRow = new HBox(8, tfDestination, btnBrowse);
            grid.add(destRow, 1, 1);

            ProgressBar progress = new ProgressBar(0);
            progress.setMaxWidth(Double.MAX_VALUE);
            progress.setVisible(false);

            Label lblStatus = new Label("");
            lblStatus.setWrapText(true);

            Button btnExport = new Button("Export");
            btnExport.setDefaultButton(true);

            btnExport.setOnAction(e -> {
                String dest = tfDestination.getText();
                if (dest == null || dest.isEmpty()) {
                    lblStatus.setText("Please select a destination folder.");
                    return;
                }
                btnExport.setDisable(true);
                progress.setVisible(true);
                progress.setProgress(-1);
                lblStatus.setText("Creating export job...");

                Map<String, Object> body = Map.of(
                    "type", cbFormat.getValue(),
                    "entityType", entityType,
                    "destinationPath", dest
                );
                new Thread(() -> {
                    try {
                        Map<String, Object> resp = ApiClient.getInstance().post("/api/exports", body);
                        Map<String, Object> job = (Map<String, Object>) resp.get("export");
                        long jobId = ((Number) job.get("id")).longValue();
                        pollUntilDone(jobId, progress, lblStatus, dialog);
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            progress.setVisible(false);
                            lblStatus.setText("Export failed: " + ex.getMessage());
                            btnExport.setDisable(false);
                        });
                    }
                }, "export-dialog").start();
            });

            VBox root = new VBox(12, grid, progress, lblStatus,
                new HBox(8, btnExport, new Button("Close") {{
                    setOnAction(e -> dialog.close());
                }}));
            root.setPadding(new Insets(16));

            dialog.setScene(new Scene(root, 520, 280));
            dialog.show();
        });
    }

    @SuppressWarnings("unchecked")
    private static void pollUntilDone(long jobId, ProgressBar progress, Label lblStatus, Stage dialog) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> resp = ApiClient.getInstance().get("/api/exports/" + jobId);
                Map<String, Object> job = (Map<String, Object>) resp.get("export");
                String status = (String) job.get("status");
                Platform.runLater(() -> lblStatus.setText("Status: " + status));
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    scheduler.shutdownNow();
                    Platform.runLater(() -> {
                        progress.setProgress(1.0);
                        if ("COMPLETED".equals(status)) {
                            String outputPath = (String) job.getOrDefault("outputPath", "");
                            String sha = (String) job.getOrDefault("sha256", "");
                            lblStatus.setText("Completed!\nFile: " + outputPath + "\nSHA-256: " + sha);
                        } else {
                            lblStatus.setText("Export failed: " + job.getOrDefault("errorMessage", "Unknown error"));
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to poll export status", e);
                scheduler.shutdownNow();
                Platform.runLater(() -> lblStatus.setText("Failed to poll status: " + e.getMessage()));
            }
        }, 1, 2, TimeUnit.SECONDS);
    }
}
