package com.eaglepoint.console.ui.kpi;

import com.eaglepoint.console.ui.shared.GlobalShortcuts;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
            GlobalShortcuts.install(scene, stage,
                ctrl::focusSearch,
                ctrl::onNewScore,
                ctrl::onExport);

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open KPI Review window", e);
        }
    }
}
