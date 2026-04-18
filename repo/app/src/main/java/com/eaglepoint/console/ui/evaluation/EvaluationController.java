package com.eaglepoint.console.ui.evaluation;

import com.eaglepoint.console.model.Scorecard;
import com.eaglepoint.console.ui.shared.ApiClient;
import com.eaglepoint.console.ui.shared.AuditTrailDialog;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    // Cycles tab
    @FXML private TableView<Map<String, Object>> tableCycles;
    @FXML private TableColumn<Map<String, Object>, String> colCycleId;
    @FXML private TableColumn<Map<String, Object>, String> colCycleName;
    @FXML private TableColumn<Map<String, Object>, String> colCycleStart;
    @FXML private TableColumn<Map<String, Object>, String> colCycleEnd;
    @FXML private TableColumn<Map<String, Object>, String> colCycleStatus;
    @FXML private Button btnActivateCycle;
    @FXML private Button btnCloseCycle;

    // Scorecards tab
    @FXML private TableView<Map<String, Object>> tableScorecards;
    @FXML private TableColumn<Map<String, Object>, String> colScId;
    @FXML private TableColumn<Map<String, Object>, String> colScEvaluatee;
    @FXML private TableColumn<Map<String, Object>, String> colScTemplate;
    @FXML private TableColumn<Map<String, Object>, String> colScCycle;
    @FXML private TableColumn<Map<String, Object>, String> colScStatus;
    @FXML private Button btnSubmitScorecard;
    @FXML private Button btnRecuse;

    // Reviews tab
    @FXML private TableView<Map<String, Object>> tableReviews;
    @FXML private TableColumn<Map<String, Object>, String> colRevId;
    @FXML private TableColumn<Map<String, Object>, String> colRevScorecard;
    @FXML private TableColumn<Map<String, Object>, String> colRevReviewer;
    @FXML private TableColumn<Map<String, Object>, String> colRevStatus;
    @FXML private TableColumn<Map<String, Object>, String> colRevNotes;
    @FXML private Button btnApprove;
    @FXML private Button btnReject;
    @FXML private Button btnAssignSecond;

    // Appeals tab
    @FXML private TableView<Map<String, Object>> tableAppeals;
    @FXML private TableColumn<Map<String, Object>, String> colAppId;
    @FXML private TableColumn<Map<String, Object>, String> colAppScorecard;
    @FXML private TableColumn<Map<String, Object>, String> colAppFiler;
    @FXML private TableColumn<Map<String, Object>, String> colAppStatus;
    @FXML private TableColumn<Map<String, Object>, String> colAppReason;
    @FXML private Button btnResolveAppeal;
    @FXML private Button btnRejectAppeal;

    private final ObservableList<Map<String, Object>> cycleItems = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> scorecardItems = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> reviewItems = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> appealItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        bindCycles();
        bindScorecards();
        bindReviews();
        bindAppeals();
        loadAll();
    }

    private void bindCycles() {
        colCycleId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("id", ""))));
        colCycleName.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("name", ""))));
        colCycleStart.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("startDate", ""))));
        colCycleEnd.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("endDate", ""))));
        colCycleStatus.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("status", ""))));
        tableCycles.setItems(cycleItems);
        tableCycles.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean selected = sel != null;
            btnActivateCycle.setDisable(!selected || !"DRAFT".equals(sel.getOrDefault("status", "")));
            btnCloseCycle.setDisable(!selected || !"ACTIVE".equals(sel.getOrDefault("status", "")));
        });
    }

    private void bindScorecards() {
        colScId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("id", ""))));
        colScEvaluatee.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("evaluateeName", ""))));
        colScTemplate.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("templateName", ""))));
        colScCycle.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("cycleName", ""))));
        colScStatus.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("status", ""))));
        tableScorecards.setItems(scorecardItems);
        tableScorecards.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean selected = sel != null;
            String scStatus = selected ? String.valueOf(sel.getOrDefault("status", "")) : "";
            btnSubmitScorecard.setDisable(!selected || (!"PENDING".equals(scStatus) && !"IN_PROGRESS".equals(scStatus)));
            btnRecuse.setDisable(!selected);
        });
        tableScorecards.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnContextMenuRequested(e -> {
                if (!row.isEmpty()) {
                    Scorecard sc = mapToScorecard(row.getItem());
                    buildScorecardContextMenu(sc).show(row, e.getScreenX(), e.getScreenY());
                }
            });
            return row;
        });
    }

    private void bindReviews() {
        colRevId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("id", ""))));
        colRevScorecard.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("scorecardId", ""))));
        colRevReviewer.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("reviewerName", ""))));
        colRevStatus.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("status", ""))));
        colRevNotes.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("notes", ""))));
        tableReviews.setItems(reviewItems);
        tableReviews.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean selected = sel != null;
            btnApprove.setDisable(!selected);
            btnReject.setDisable(!selected);
            btnAssignSecond.setDisable(!selected);
        });
    }

    private void bindAppeals() {
        colAppId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("id", ""))));
        colAppScorecard.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("scorecardId", ""))));
        colAppFiler.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("filedByName", ""))));
        colAppStatus.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("status", ""))));
        colAppReason.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().getOrDefault("reason", ""))));
        tableAppeals.setItems(appealItems);
        tableAppeals.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean selected = sel != null;
            btnResolveAppeal.setDisable(!selected);
            btnRejectAppeal.setDisable(!selected);
        });
    }

    @SuppressWarnings("unchecked")
    private void loadAll() {
        new Thread(() -> {
            try {
                Map<String, Object> cyclesResp = ApiClient.getInstance().get("/api/cycles?pageSize=100");
                List<Map<String, Object>> cycles = (List<Map<String, Object>>) cyclesResp.getOrDefault("data", List.of());
                Map<String, Object> scResp = ApiClient.getInstance().get("/api/scorecards?pageSize=100");
                List<Map<String, Object>> scorecards = (List<Map<String, Object>>) scResp.getOrDefault("data", List.of());
                Map<String, Object> revResp = ApiClient.getInstance().get("/api/reviews?pageSize=100");
                List<Map<String, Object>> reviews = (List<Map<String, Object>>) revResp.getOrDefault("data", List.of());
                Map<String, Object> appResp = ApiClient.getInstance().get("/api/appeals?pageSize=100");
                List<Map<String, Object>> appeals = (List<Map<String, Object>>) appResp.getOrDefault("data", List.of());
                Platform.runLater(() -> {
                    cycleItems.setAll(cycles);
                    scorecardItems.setAll(scorecards);
                    reviewItems.setAll(reviews);
                    appealItems.setAll(appeals);
                });
            } catch (Exception e) {
                log.error("Failed to load evaluation data", e);
            }
        }, "eval-load").start();
    }

    @FXML private void onRefresh() { loadAll(); }

    @FXML private void onNewCycle() {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("New Evaluation Cycle");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tfName = new TextField(); tfName.setPromptText("Cycle name");
        DatePicker dpStart = new DatePicker();
        DatePicker dpEnd = new DatePicker();
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(8,
            new Label("Name:"), tfName,
            new Label("Start Date:"), dpStart,
            new Label("End Date:"), dpEnd);
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? Map.of(
            "name", tfName.getText(),
            "startDate", dpStart.getValue() != null ? dpStart.getValue().toString() : "",
            "endDate", dpEnd.getValue() != null ? dpEnd.getValue().toString() : ""
        ) : null);
        dialog.showAndWait().ifPresent(body -> post("/api/cycles", body));
    }

    @FXML private void onActivateCycle() {
        // Cycle lifecycle transitions are explicit endpoints — see
        // EvaluationRoutes.java for the contract.  Posting an empty body is
        // intentional; the server derives the new status from the URL.
        Map<String, Object> sel = tableCycles.getSelectionModel().getSelectedItem();
        if (sel != null) {
            long id = ((Number) sel.get("id")).longValue();
            post("/api/cycles/" + id + "/activate", Map.of());
        }
    }

    @FXML private void onCloseCycle() {
        Map<String, Object> sel = tableCycles.getSelectionModel().getSelectedItem();
        if (sel != null) {
            long id = ((Number) sel.get("id")).longValue();
            post("/api/cycles/" + id + "/close", Map.of());
        }
    }

    @FXML private void onNewScorecard() {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("New Scorecard");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tfTemplateId = new TextField(); tfTemplateId.setPromptText("Template ID");
        TextField tfEvaluateeId = new TextField(); tfEvaluateeId.setPromptText("Evaluatee User ID");
        TextField tfCycleId = new TextField(); tfCycleId.setPromptText("Cycle ID");
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(8,
            new Label("Template ID:"), tfTemplateId,
            new Label("Evaluatee ID:"), tfEvaluateeId,
            new Label("Cycle ID:"), tfCycleId);
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? Map.of(
            "templateId", Long.parseLong(tfTemplateId.getText()),
            "evaluateeId", Long.parseLong(tfEvaluateeId.getText()),
            "cycleId", Long.parseLong(tfCycleId.getText())
        ) : null);
        dialog.showAndWait().ifPresent(body -> post("/api/scorecards", body));
    }

    @FXML private void onSubmitScorecard() {
        Map<String, Object> sel = tableScorecards.getSelectionModel().getSelectedItem();
        if (sel != null) {
            long id = ((Number) sel.get("id")).longValue();
            post("/api/scorecards/" + id + "/submit", Map.of());
        }
    }

    @FXML private void onRecuse() {
        Map<String, Object> sel = tableScorecards.getSelectionModel().getSelectedItem();
        if (sel != null) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Flag Conflict");
            d.setHeaderText("Enter recusal reason:");
            d.showAndWait().ifPresent(reason -> {
                long id = ((Number) sel.get("id")).longValue();
                post("/api/scorecards/" + id + "/recuse", Map.of("reason", reason));
            });
        }
    }

    @FXML private void onApproveReview() {
        Map<String, Object> sel = tableReviews.getSelectionModel().getSelectedItem();
        if (sel != null) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Approve Review"); d.setHeaderText("Notes (optional):");
            d.showAndWait().ifPresent(notes -> {
                long id = ((Number) sel.get("id")).longValue();
                post("/api/reviews/" + id + "/approve", Map.of("notes", notes));
            });
        }
    }

    @FXML private void onRejectReview() {
        Map<String, Object> sel = tableReviews.getSelectionModel().getSelectedItem();
        if (sel != null) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Reject Review"); d.setHeaderText("Notes:");
            d.showAndWait().ifPresent(notes -> {
                long id = ((Number) sel.get("id")).longValue();
                post("/api/reviews/" + id + "/reject", Map.of("notes", notes));
            });
        }
    }

    @FXML private void onAssignSecond() {
        Map<String, Object> sel = tableReviews.getSelectionModel().getSelectedItem();
        if (sel != null) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Assign 2nd Reviewer"); d.setHeaderText("Reviewer User ID:");
            d.showAndWait().ifPresent(reviewerId -> {
                long id = ((Number) sel.get("id")).longValue();
                post("/api/reviews/" + id + "/assign-second",
                    Map.of("reviewerId", Long.parseLong(reviewerId)));
            });
        }
    }

    @FXML private void onFileAppeal() {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("File Appeal");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tfScorecardId = new TextField(); tfScorecardId.setPromptText("Scorecard ID");
        TextArea taReason = new TextArea(); taReason.setPromptText("Appeal reason");
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(8,
            new Label("Scorecard ID:"), tfScorecardId,
            new Label("Reason:"), taReason);
        vbox.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? Map.of(
            "scorecardId", Long.parseLong(tfScorecardId.getText()),
            "reason", taReason.getText()
        ) : null);
        dialog.showAndWait().ifPresent(body -> post("/api/appeals", body));
    }

    @FXML private void onResolveAppeal() {
        Map<String, Object> sel = tableAppeals.getSelectionModel().getSelectedItem();
        if (sel != null) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Resolve Appeal"); d.setHeaderText("Resolution notes:");
            d.showAndWait().ifPresent(notes -> {
                long id = ((Number) sel.get("id")).longValue();
                post("/api/appeals/" + id + "/resolve", Map.of("notes", notes));
            });
        }
    }

    @FXML private void onRejectAppeal() {
        Map<String, Object> sel = tableAppeals.getSelectionModel().getSelectedItem();
        if (sel != null) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Reject Appeal"); d.setHeaderText("Rejection notes:");
            d.showAndWait().ifPresent(notes -> {
                long id = ((Number) sel.get("id")).longValue();
                post("/api/appeals/" + id + "/reject", Map.of("notes", notes));
            });
        }
    }

    public void submitScorecard(Scorecard sc) {
        post("/api/scorecards/" + sc.getId() + "/submit", Map.of());
    }

    public void flagConflict(Scorecard sc) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Flag Conflict"); d.setHeaderText("Reason:");
        d.showAndWait().ifPresent(reason ->
            post("/api/scorecards/" + sc.getId() + "/recuse", Map.of("reason", reason)));
    }

    private ContextMenu buildScorecardContextMenu(Scorecard sc) {
        ContextMenu menu = new ContextMenu();
        MenuItem submit = new MenuItem("Submit");
        submit.setOnAction(e -> submitScorecard(sc));
        MenuItem flag = new MenuItem("Flag Conflict");
        flag.setOnAction(e -> flagConflict(sc));
        MenuItem audit = new MenuItem("View Audit Trail");
        audit.setOnAction(e -> AuditTrailDialog.show("SCORECARD", sc.getId()));
        menu.getItems().addAll(submit, flag, new SeparatorMenuItem(), audit);
        return menu;
    }

    private Scorecard mapToScorecard(Map<String, Object> data) {
        Scorecard sc = new Scorecard();
        sc.setId(((Number) data.getOrDefault("id", 0)).longValue());
        sc.setStatus(String.valueOf(data.getOrDefault("status", "DRAFT")));
        return sc;
    }

    private void post(String path, Object body) {
        new Thread(() -> {
            try {
                ApiClient.getInstance().post(path, body);
                Platform.runLater(this::loadAll);
            } catch (Exception e) {
                log.error("POST failed: {}", path, e);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private void put(String path, Object body) {
        new Thread(() -> {
            try {
                ApiClient.getInstance().put(path, body);
                Platform.runLater(this::loadAll);
            } catch (Exception e) {
                log.error("PUT failed: {}", path, e);
            }
        }).start();
    }
}
