package com.eaglepoint.console.ui.kpi;

import com.eaglepoint.console.ui.shared.ApiClient;
import com.eaglepoint.console.ui.shared.AuditTrailDialog;
import com.eaglepoint.console.ui.shared.ExportDialog;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class KpiReviewController {

    private static final Logger log = LoggerFactory.getLogger(KpiReviewController.class);

    @FXML private TableView<Map<String, Object>> tableScores;
    @FXML private TableColumn<Map<String, Object>, String> colId;
    @FXML private TableColumn<Map<String, Object>, String> colKpiName;
    @FXML private TableColumn<Map<String, Object>, String> colValue;
    @FXML private TableColumn<Map<String, Object>, String> colUnit;
    @FXML private TableColumn<Map<String, Object>, String> colDate;
    @FXML private TableColumn<Map<String, Object>, String> colServiceArea;
    @FXML private TableColumn<Map<String, Object>, String> colNotes;
    @FXML private TableColumn<Map<String, Object>, String> colComputedBy;
    @FXML private TextField tfFilter;
    @FXML private ComboBox<String> cbKpiFilter;
    @FXML private ComboBox<String> cbServiceAreaFilter;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Label lblPage;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;

    private final ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
    private int currentPage = 1;
    private static final int PAGE_SIZE = 50;
    private Stage ownerStage;

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    @FXML
    public void initialize() {
        colId.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("id", ""))));
        colKpiName.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("kpiName", ""))));
        colValue.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("value", ""))));
        colUnit.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("unit", ""))));
        colDate.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("scoreDate", ""))));
        colServiceArea.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("serviceAreaName", ""))));
        colNotes.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("notes", ""))));
        colComputedBy.setCellValueFactory(cd -> new SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("computedBy", ""))));

        tableScores.setItems(items);

        // Right-click context menu
        tableScores.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem auditItem = new MenuItem("View Audit Trail");
            auditItem.setOnAction(e -> {
                Map<String, Object> item = row.getItem();
                if (item != null) {
                    long id = ((Number) item.get("id")).longValue();
                    AuditTrailDialog.show("KPI_SCORE", id);
                }
            });
            menu.getItems().add(auditItem);
            row.setContextMenu(menu);
            return row;
        });

        loadData();
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        new Thread(() -> {
            try {
                String url = "/api/kpi-scores?page=" + currentPage + "&pageSize=" + PAGE_SIZE;
                Map<String, Object> resp = ApiClient.getInstance().get(url);
                List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getOrDefault("data", List.of());
                Platform.runLater(() -> {
                    items.setAll(data);
                    lblPage.setText("Page " + currentPage);
                });
            } catch (Exception e) {
                log.error("Failed to load KPI scores", e);
            }
        }, "kpi-load").start();
    }

    @FXML private void onRefresh() { loadData(); }
    @FXML private void onPrevPage() { if (currentPage > 1) { currentPage--; loadData(); } }
    @FXML private void onNextPage() { currentPage++; loadData(); }
    @FXML private void onApplyFilter() { currentPage = 1; loadData(); }
    @FXML private void onClearFilter() {
        tfFilter.clear();
        cbKpiFilter.setValue(null);
        cbServiceAreaFilter.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        currentPage = 1;
        loadData();
    }

    @FXML void onNewScore() {
        // Simple dialog to enter a new KPI score
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Record KPI Score");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Simplified form
        TextField tfKpiId = new TextField();
        tfKpiId.setPromptText("KPI ID");
        TextField tfValue = new TextField();
        tfValue.setPromptText("Value");
        TextField tfDate = new TextField();
        tfDate.setPromptText("Date (YYYY-MM-DD)");
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(8,
            new Label("KPI ID:"), tfKpiId,
            new Label("Value:"), tfValue,
            new Label("Score Date:"), tfDate);
        vbox.setPadding(new javafx.geometry.Insets(12));
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return Map.of(
                    "kpiId", Long.parseLong(tfKpiId.getText()),
                    "value", Double.parseDouble(tfValue.getText()),
                    "scoreDate", tfDate.getText()
                );
            }
            return null;
        });
        dialog.showAndWait().ifPresent(data -> {
            new Thread(() -> {
                try {
                    ApiClient.getInstance().post("/api/kpi-scores", data);
                    Platform.runLater(this::loadData);
                } catch (Exception e) {
                    log.error("Failed to record KPI score", e);
                }
            }).start();
        });
    }

    @FXML void onExport() {
        if (ownerStage != null) ExportDialog.show("KPI_SCORES", ownerStage);
    }
}
