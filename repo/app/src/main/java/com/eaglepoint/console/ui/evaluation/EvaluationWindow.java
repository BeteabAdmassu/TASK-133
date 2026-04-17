package com.eaglepoint.console.ui.evaluation;

import com.eaglepoint.console.ui.reports.ReportsWindow;
import com.eaglepoint.console.ui.shared.GlobalShortcuts;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationWindow {

    private static final Logger log = LoggerFactory.getLogger(EvaluationWindow.class);
    private static Stage stage;

    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                EvaluationWindow.class.getResource("/fxml/evaluation.fxml"));
            VBox root = loader.load();

            stage = new Stage();
            stage.initOwner(owner);
            stage.setTitle("Evaluation Management");

            Scene scene = new Scene(root, 1100, 750);
            scene.getStylesheets().add(
                EvaluationWindow.class.getResource("/css/application.css").toExternalForm());

            // The evaluation window has no bound find/new/export actions yet;
            // install the shortcuts so users get a consistent "not available
            // here" prompt instead of silent no-ops, and keep Ctrl+L wired.
            GlobalShortcuts.install(scene, stage, null, null,
                () -> ReportsWindow.show(stage));

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open Evaluation window", e);
        }
    }
}
