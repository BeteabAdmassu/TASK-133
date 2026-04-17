package com.eaglepoint.console.ui.shared;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AuditTrailDialog {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailDialog.class);

    @SuppressWarnings("unchecked")
    public static void show(String entityType, long entityId) {
        Platform.runLater(() -> {
            try {
                String path = "/api/audit-trail?entityType=" + entityType
                    + "&entityId=" + entityId + "&pageSize=100";
                Map<String, Object> resp = ApiClient.getInstance().get(path);
                List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getOrDefault("data", List.of());

                TableView<Map<String, Object>> table = new TableView<>();
                TableColumn<Map<String, Object>, String> colTs = new TableColumn<>("Timestamp");
                colTs.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                    String.valueOf(cd.getValue().getOrDefault("createdAt", ""))));
                colTs.setPrefWidth(160);

                TableColumn<Map<String, Object>, String> colAction = new TableColumn<>("Action");
                colAction.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                    String.valueOf(cd.getValue().getOrDefault("action", ""))));
                colAction.setPrefWidth(100);

                TableColumn<Map<String, Object>, String> colUser = new TableColumn<>("User");
                colUser.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                    String.valueOf(cd.getValue().getOrDefault("userId", ""))));
                colUser.setPrefWidth(80);

                TableColumn<Map<String, Object>, String> colNotes = new TableColumn<>("Notes");
                colNotes.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                    String.valueOf(cd.getValue().getOrDefault("notes", ""))));
                colNotes.setPrefWidth(300);

                table.getColumns().addAll(colTs, colAction, colUser, colNotes);
                table.setItems(FXCollections.observableArrayList(items));
                table.setPlaceholder(new Label("No audit trail records."));

                VBox root = new VBox(8);
                root.getChildren().addAll(
                    new Label("Audit Trail — " + entityType + " #" + entityId),
                    table
                );
                root.setStyle("-fx-padding: 12;");

                Stage stage = new Stage();
                stage.setTitle("Audit Trail — " + entityType + " #" + entityId);
                stage.setScene(new Scene(root, 700, 450));
                stage.show();
            } catch (Exception e) {
                log.error("Failed to load audit trail", e);
                showError("Failed to load audit trail: " + e.getMessage());
            }
        });
    }

    private static void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Error");
        alert.showAndWait();
    }
}
