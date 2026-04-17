package com.eaglepoint.console.ui.evaluation;

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

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open Evaluation window", e);
        }
    }
}
