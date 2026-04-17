package com.eaglepoint.console.ui.reports;

import com.eaglepoint.console.ui.shared.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReportsController {

    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);

    @FXML private ComboBox<String> cbReportType;
    @FXML private ComboBox<String> cbFormat;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private ComboBox<String> cbServiceArea;
    @FXML private TextField tfDestination;
    @FXML private ComboBox<String> cbScheduleType;
    @FXML private javafx.scene.layout.VBox progressSection;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;
    @FXML private javafx.scene.layout.VBox resultSection;
    @FXML private Label lblOutputPath;
    @FXML private Label lblSha256;
    @FXML private Label lblError;
    @FXML private Button btnGenerate;

    private Stage ownerStage;

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }

    @FXML
    public void initialize() {
        cbReportType.getItems().addAll(
            "EVALUATION_RESULTS", "KPI_SCORES", "BED_BOARD", "PICKUP_POINTS",
            "LEADER_ASSIGNMENTS", "AUDIT_TRAIL", "ROUTE_IMPORTS"
        );
        cbFormat.getItems().addAll("EXCEL", "PDF", "CSV");
        cbFormat.setValue("EXCEL");
        // Scheduling from the Reports window is handled by the Scheduled Jobs
        // admin screen (REPORT jobs in /api/system/scheduled-jobs). The combo
        // here is disabled to prevent operators from thinking they can schedule
        // ad-hoc reports from this dialog.
        cbScheduleType.getItems().add("One-time");
        cbScheduleType.setValue("One-time");
        cbScheduleType.setDisable(true);
        cbScheduleType.setTooltip(new Tooltip(
            "Recurring reports are configured in the Scheduled Jobs admin screen (REPORT job type)."
        ));
    }

    @FXML private void onBrowseDestination() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Output Folder");
        File dir = dc.showDialog(ownerStage);
        if (dir != null) tfDestination.setText(dir.getAbsolutePath());
    }

    @FXML void onGenerate() {
        String reportType = cbReportType.getValue();
        String format = cbFormat.getValue();
        String dest = tfDestination.getText();

        if (reportType == null || reportType.isEmpty()) {
            showError("Please select a report type.");
            return;
        }
        if (dest == null || dest.isEmpty()) {
            showError("Please select a destination folder.");
            return;
        }

        btnGenerate.setDisable(true);
        progressSection.setVisible(true);
        resultSection.setVisible(false);
        lblError.setVisible(false);
        progressBar.setProgress(-1);
        lblProgress.setText("Creating export job...");

        Map<String, Object> body = Map.of(
            "type", format != null ? format : "EXCEL",
            "entityType", reportType,
            "destinationPath", dest
        );

        new Thread(() -> {
            try {
                Map<String, Object> resp = ApiClient.getInstance().post("/api/exports", body);
                @SuppressWarnings("unchecked")
                Map<String, Object> job = (Map<String, Object>) resp.get("export");
                long jobId = ((Number) job.get("id")).longValue();
                pollStatus(jobId);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressSection.setVisible(false);
                    showError("Failed to create export: " + e.getMessage());
                    btnGenerate.setDisable(false);
                });
            }
        }, "reports-export").start();
    }

    @SuppressWarnings("unchecked")
    private void pollStatus(long jobId) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> resp = ApiClient.getInstance().get("/api/exports/" + jobId);
                Map<String, Object> job = (Map<String, Object>) resp.get("export");
                String status = (String) job.get("status");
                Platform.runLater(() -> lblProgress.setText("Status: " + status));
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    scheduler.shutdownNow();
                    Platform.runLater(() -> {
                        progressSection.setVisible(false);
                        btnGenerate.setDisable(false);
                        if ("COMPLETED".equals(status)) {
                            // Match the server DTO — ExportJob serialises
                            // outputFilePath/sha256Hash (see ExportJob model).
                            Object outPath = job.get("outputFilePath");
                            Object sha = job.get("sha256Hash");
                            lblOutputPath.setText(outPath != null ? String.valueOf(outPath) : "");
                            lblSha256.setText(sha != null ? String.valueOf(sha) : "");
                            resultSection.setVisible(true);
                        } else {
                            showError("Export failed: " + job.getOrDefault("errorMessage", "Unknown error"));
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to poll export", e);
                scheduler.shutdownNow();
                Platform.runLater(() -> {
                    progressSection.setVisible(false);
                    showError("Failed to poll status: " + e.getMessage());
                    btnGenerate.setDisable(false);
                });
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    @FXML private void onClear() {
        cbReportType.setValue(null);
        cbFormat.setValue("EXCEL");
        dpFrom.setValue(null);
        dpTo.setValue(null);
        tfDestination.clear();
        progressSection.setVisible(false);
        resultSection.setVisible(false);
        lblError.setVisible(false);
        btnGenerate.setDisable(false);
    }

    @FXML private void onOpenFile() {
        String path = lblOutputPath.getText();
        if (path != null && !path.isEmpty()) {
            try {
                new ProcessBuilder("explorer.exe", "/select,", path).start();
            } catch (Exception e) {
                log.warn("Failed to open file in explorer", e);
            }
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
