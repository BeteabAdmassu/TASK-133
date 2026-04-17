package com.eaglepoint.console.ui.bed;

import com.eaglepoint.console.model.Bed;
import com.eaglepoint.console.ui.shared.ApiClient;
import com.eaglepoint.console.ui.shared.ContextMenuFactory;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BedBoardController {

    private static final Logger log = LoggerFactory.getLogger(BedBoardController.class);

    @FXML private FlowPane bedGrid;
    @FXML private ComboBox<String> cbBuildingFilter;
    @FXML private ComboBox<String> cbStateFilter;
    @FXML private TextField tfFilter;
    @FXML private Label lblBedCount;

    private final List<Map<String, Object>> allBeds = new ArrayList<>();
    private Timeline autoRefresh;

    @FXML
    public void initialize() {
        cbStateFilter.getItems().addAll("AVAILABLE", "OCCUPIED", "RESERVED", "CLEANING", "MAINTENANCE", "OUT_OF_SERVICE");

        autoRefresh = new Timeline(new KeyFrame(Duration.seconds(30), e -> loadBeds()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();

        loadBeds();
    }

    @SuppressWarnings("unchecked")
    private void loadBeds() {
        new Thread(() -> {
            try {
                Map<String, Object> resp = ApiClient.getInstance().get("/api/beds?pageSize=500");
                List<Map<String, Object>> beds = (List<Map<String, Object>>) resp.getOrDefault("data", List.of());
                Platform.runLater(() -> {
                    allBeds.clear();
                    allBeds.addAll(beds);
                    renderGrid();
                });
            } catch (Exception e) {
                log.error("Failed to load beds", e);
            }
        }, "bed-load").start();
    }

    private void renderGrid() {
        bedGrid.getChildren().clear();
        String stateFilter = cbStateFilter.getValue();
        String textFilter = tfFilter.getText();
        int count = 0;
        for (Map<String, Object> bed : allBeds) {
            if (!BedDisplayFilter.matches(bed, stateFilter, textFilter)) continue;
            bedGrid.getChildren().add(createBedCell(bed));
            count++;
        }
        lblBedCount.setText(count + " beds");
    }

    private VBox createBedCell(Map<String, Object> bedData) {
        String state = String.valueOf(bedData.getOrDefault("state", "AVAILABLE"));
        String label = String.valueOf(bedData.getOrDefault("bedLabel", "?"));
        String cssClass = stateToCssClass(state);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("bed-cell-label");
        Label stateLbl = new Label(state.replace("_", " "));
        stateLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(255,255,255,0.85);");

        VBox cell = new VBox(2, lbl, stateLbl);
        cell.setAlignment(Pos.CENTER);
        cell.getStyleClass().addAll("bed-cell", cssClass);

        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                Bed bed = mapToBed(bedData);
                ContextMenuFactory.buildForBed(bed, this)
                    .show(cell, e.getScreenX(), e.getScreenY());
            }
        });

        return cell;
    }

    public void showTransitionDialog(Bed bed) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Transition Bed #" + bed.getId());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> cbToState = new ComboBox<>();
        cbToState.getItems().addAll("AVAILABLE", "OCCUPIED", "RESERVED", "CLEANING", "MAINTENANCE", "OUT_OF_SERVICE");
        TextField tfResident = new TextField();
        tfResident.setPromptText("Resident ID (for OCCUPIED only)");
        TextField tfReason = new TextField();
        tfReason.setPromptText("Reason");
        TextField tfNotes = new TextField();
        tfNotes.setPromptText("Notes");

        cbToState.setOnAction(e -> {
            boolean needsResident = "OCCUPIED".equals(cbToState.getValue());
            tfResident.setDisable(!needsResident);
        });
        tfResident.setDisable(true);

        VBox vbox = new VBox(8,
            new Label("To State:"), cbToState,
            new Label("Resident ID:"), tfResident,
            new Label("Reason:"), tfReason,
            new Label("Notes:"), tfNotes);
        vbox.setStyle("-fx-padding: 12;");
        dialog.getDialogPane().setContent(vbox);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? Map.of(
            "toState", cbToState.getValue() != null ? cbToState.getValue() : "",
            "residentId", tfResident.getText(),
            "reason", tfReason.getText(),
            "notes", tfNotes.getText()
        ) : null);

        dialog.showAndWait().ifPresent(body -> {
            new Thread(() -> {
                try {
                    ApiClient.getInstance().post("/api/beds/" + bed.getId() + "/transition", body);
                    Platform.runLater(this::loadBeds);
                } catch (Exception e) {
                    log.error("Failed to transition bed", e);
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Failed: " + e.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                    });
                }
            }).start();
        });
    }

    private Bed mapToBed(Map<String, Object> data) {
        Bed bed = new Bed();
        bed.setId(((Number) data.getOrDefault("id", 0)).longValue());
        bed.setBedLabel(String.valueOf(data.getOrDefault("bedLabel", "")));
        return bed;
    }

    private String stateToCssClass(String state) {
        return switch (state) {
            case "AVAILABLE" -> "bed-available";
            case "OCCUPIED" -> "bed-occupied";
            case "RESERVED" -> "bed-reserved";
            case "CLEANING" -> "bed-cleaning";
            case "MAINTENANCE" -> "bed-maintenance";
            case "OUT_OF_SERVICE" -> "bed-out-of-service";
            default -> "bed-available";
        };
    }

    public void stopRefresh() {
        if (autoRefresh != null) autoRefresh.stop();
    }

    /** Ctrl+F handler: move keyboard focus to the filter text field. */
    public void focusSearch() {
        if (tfFilter != null) tfFilter.requestFocus();
    }

    /**
     * Ctrl+N handler: open the "new bed" creation dialog.  Posts to
     * {@code POST /api/beds} with {@code roomId} + {@code bedLabel}; the
     * bed defaults to {@code AVAILABLE} state per the bed state machine.
     */
    public void openNewBed() {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("New Bed");
        dialog.setHeaderText("Create a new bed record");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfRoomId = new TextField();
        tfRoomId.setPromptText("Room ID");
        TextField tfLabel = new TextField();
        tfLabel.setPromptText("Bed label (e.g. 101A)");

        VBox vbox = new VBox(8,
            new Label("Room ID:"), tfRoomId,
            new Label("Bed Label:"), tfLabel);
        vbox.setStyle("-fx-padding: 12;");
        dialog.getDialogPane().setContent(vbox);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? Map.of(
            "roomId", tfRoomId.getText().trim(),
            "bedLabel", tfLabel.getText().trim()
        ) : null);

        dialog.showAndWait().ifPresent(body -> new Thread(() -> {
            try {
                long roomId = Long.parseLong((String) body.get("roomId"));
                String label = (String) body.get("bedLabel");
                if (label.isEmpty()) throw new IllegalArgumentException("bedLabel required");
                ApiClient.getInstance().post("/api/beds",
                    Map.of("roomId", roomId, "bedLabel", label));
                Platform.runLater(this::loadBeds);
            } catch (Exception e) {
                log.error("Failed to create bed", e);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Failed: " + e.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                });
            }
        }).start());
    }

    /**
     * "Transfer Resident" workflow invoked from the bed context menu.
     *
     * <p>A transfer is two coupled state transitions the state machine
     * already validates:
     * <ol>
     *   <li>Source bed {@code OCCUPIED → AVAILABLE} (checkout)</li>
     *   <li>Destination bed {@code AVAILABLE → OCCUPIED} with the same
     *       {@code residentId} and a reason prefixed with {@code TRANSFER:}
     *       so the audit trail is traceable.</li>
     * </ol>
     */
    public void showTransferDialog(Bed source) {
        if (!"OCCUPIED".equals(source.getState())) {
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                "Only OCCUPIED beds can be transferred.", ButtonType.OK);
            info.showAndWait();
            return;
        }
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Transfer Resident from bed #" + source.getId());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfDest = new TextField();
        tfDest.setPromptText("Destination bed ID (must be AVAILABLE)");
        TextField tfResident = new TextField();
        tfResident.setPromptText("Resident ID");
        TextField tfReason = new TextField();
        tfReason.setPromptText("Transfer reason");

        VBox vbox = new VBox(8,
            new Label("Destination Bed ID:"), tfDest,
            new Label("Resident ID:"), tfResident,
            new Label("Reason:"), tfReason);
        vbox.setStyle("-fx-padding: 12;");
        dialog.getDialogPane().setContent(vbox);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? Map.of(
            "destId", tfDest.getText().trim(),
            "residentId", tfResident.getText().trim(),
            "reason", tfReason.getText().trim()
        ) : null);

        dialog.showAndWait().ifPresent(body -> new Thread(() -> {
            try {
                long destId = Long.parseLong((String) body.get("destId"));
                String reason = "TRANSFER: " + body.getOrDefault("reason", "");
                // 1. Checkout source.
                ApiClient.getInstance().post("/api/beds/" + source.getId() + "/transition",
                    Map.of("toState", "AVAILABLE", "reason", reason));
                // 2. Occupy destination with the same resident id.
                ApiClient.getInstance().post("/api/beds/" + destId + "/transition",
                    Map.of(
                        "toState", "OCCUPIED",
                        "residentId", body.get("residentId"),
                        "reason", reason
                    ));
                Platform.runLater(this::loadBeds);
            } catch (Exception e) {
                log.error("Failed to transfer bed", e);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Transfer failed: " + e.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                });
            }
        }).start());
    }

    @FXML private void onRefresh() { loadBeds(); }
    @FXML private void onApplyFilter() { renderGrid(); }
    @FXML private void onClearFilter() {
        tfFilter.clear();
        cbBuildingFilter.setValue(null);
        cbStateFilter.setValue(null);
        renderGrid();
    }
}
