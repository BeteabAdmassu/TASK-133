package com.eaglepoint.console.ui.pickup;

import com.eaglepoint.console.model.PickupPoint;
import com.eaglepoint.console.ui.AuthSession;
import com.eaglepoint.console.ui.shared.ApiClient;
import com.eaglepoint.console.ui.shared.ContextMenuFactory;
import com.eaglepoint.console.ui.shared.ExportDialog;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class PickupPointController {

    private static final Logger log = LoggerFactory.getLogger(PickupPointController.class);

    @FXML private TableView<Map<String, Object>> tablePickupPoints;
    @FXML private TableColumn<Map<String, Object>, String> colId;
    @FXML private TableColumn<Map<String, Object>, String> colAddress;
    @FXML private TableColumn<Map<String, Object>, String> colZipCode;
    @FXML private TableColumn<Map<String, Object>, String> colCommunity;
    @FXML private TableColumn<Map<String, Object>, String> colCapacity;
    @FXML private TableColumn<Map<String, Object>, String> colStatus;
    @FXML private TableColumn<Map<String, Object>, String> colPausedUntil;
    @FXML private TextField tfFilter;
    @FXML private ComboBox<String> cbCommunityFilter;
    @FXML private ComboBox<String> cbStatusFilter;
    @FXML private Label lblPage;

    private final ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
    private int currentPage = 1;
    private static final int PAGE_SIZE = 50;
    private Stage ownerStage;

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }

    @FXML
    public void initialize() {
        boolean isAdmin = AuthSession.getInstance().getCurrentUser()
            .map(u -> "SYSTEM_ADMIN".equals(u.getRole())).orElse(false);

        colId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("id", ""))));
        colAddress.setCellValueFactory(cd -> {
            String addr = isAdmin
                ? String.valueOf(cd.getValue().getOrDefault("address", ""))
                : "[MASKED]";
            return new SimpleStringProperty(addr);
        });
        colZipCode.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("zipCode", ""))));
        colCommunity.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("communityName", ""))));
        colCapacity.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("capacity", ""))));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("status", ""))));
        colPausedUntil.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("pausedUntil", ""))));

        cbStatusFilter.getItems().addAll("ACTIVE", "PAUSED", "INACTIVE");

        tablePickupPoints.setItems(items);
        tablePickupPoints.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnContextMenuRequested(e -> {
                if (!row.isEmpty()) {
                    Map<String, Object> data = row.getItem();
                    PickupPoint pp = mapToPickupPoint(data);
                    ContextMenuFactory.buildForPickupPoint(pp, this)
                        .show(row, e.getScreenX(), e.getScreenY());
                }
            });
            return row;
        });

        loadData();
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        new Thread(() -> {
            try {
                String url = "/api/pickup-points?page=" + currentPage + "&pageSize=" + PAGE_SIZE;
                Map<String, Object> resp = ApiClient.getInstance().get(url);
                List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getOrDefault("data", List.of());
                Platform.runLater(() -> {
                    items.setAll(data);
                    lblPage.setText("Page " + currentPage);
                });
            } catch (Exception e) {
                log.error("Failed to load pickup points", e);
            }
        }, "pp-load").start();
    }

    public void showPauseDialog(PickupPoint pp) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Pause Service — " + pp.getId());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tfReason = new TextField();
        tfReason.setPromptText("Pause reason");
        DatePicker dpUntil = new DatePicker();
        dpUntil.setPromptText("Paused until (optional)");
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(8,
            new Label("Reason:"), tfReason,
            new Label("Paused Until:"), dpUntil);
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ?
            Map.of("reason", tfReason.getText(),
                   "pausedUntil", dpUntil.getValue() != null ? dpUntil.getValue().toString() : "") : null);
        dialog.showAndWait().ifPresent(body -> {
            new Thread(() -> {
                try {
                    ApiClient.getInstance().post("/api/pickup-points/" + pp.getId() + "/pause", body);
                    Platform.runLater(this::loadData);
                } catch (Exception e) { log.error("Failed to pause pickup point", e); }
            }).start();
        });
    }

    public void showResumeDialog(PickupPoint pp) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Resume service for pickup point #" + pp.getId() + "?", ButtonType.YES, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                new Thread(() -> {
                    try {
                        ApiClient.getInstance().post("/api/pickup-points/" + pp.getId() + "/resume", Map.of());
                        Platform.runLater(this::loadData);
                    } catch (Exception e) { log.error("Failed to resume pickup point", e); }
                }).start();
            }
        });
    }

    private PickupPoint mapToPickupPoint(Map<String, Object> data) {
        PickupPoint pp = new PickupPoint();
        pp.setId(((Number) data.getOrDefault("id", 0)).longValue());
        pp.setStatus(String.valueOf(data.getOrDefault("status", "ACTIVE")));
        return pp;
    }

    @FXML private void onRefresh() { loadData(); }
    @FXML private void onPrevPage() { if (currentPage > 1) { currentPage--; loadData(); } }
    @FXML private void onNextPage() { currentPage++; loadData(); }
    @FXML private void onApplyFilter() { currentPage = 1; loadData(); }
    @FXML private void onClearFilter() {
        tfFilter.clear();
        cbCommunityFilter.setValue(null);
        cbStatusFilter.setValue(null);
        currentPage = 1;
        loadData();
    }
    @FXML private void onExport() {
        if (ownerStage != null) ExportDialog.show("PICKUP_POINTS", ownerStage);
    }
}
