package com.eaglepoint.console.ui.kpi;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KpiReviewWindow {

    private static final Logger log = LoggerFactory.getLogger(KpiReviewWindow.class);
    private static Stage stage;

    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                KpiReviewWindow.class.getResource("/fxml/kpi-review.fxml"));
            VBox root = loader.load();
            KpiReviewController ctrl = loader.getController();

            stage = new Stage();
            stage.initOwner(owner);
            stage.setTitle("KPI Reviews");
            ctrl.setOwnerStage(stage);

            Scene scene = new Scene(root, 1000, 700);
            scene.getStylesheets().add(
                KpiReviewWindow.class.getResource("/css/application.css").toExternalForm());
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                ctrl::onNewScore);
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                ctrl::onExport);
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> {/* focus filter - handled in controller */});

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open KPI Review window", e);
        }
    }
}
