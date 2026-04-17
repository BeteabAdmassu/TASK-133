package com.eaglepoint.console.ui.reports;

import com.eaglepoint.console.ui.shared.GlobalShortcuts;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportsWindow {

    private static final Logger log = LoggerFactory.getLogger(ReportsWindow.class);
    private static Stage stage;

    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                ReportsWindow.class.getResource("/fxml/reports.fxml"));
            VBox root = loader.load();
            ReportsController ctrl = loader.getController();

            stage = new Stage();
            stage.initOwner(owner);
            stage.setTitle("Reports & Exports");
            ctrl.setOwnerStage(stage);

            Scene scene = new Scene(root, 900, 650);
            scene.getStylesheets().add(
                ReportsWindow.class.getResource("/css/application.css").toExternalForm());

            GlobalShortcuts.install(scene, stage,
                null,            // Ctrl+F: no list to filter here
                ctrl::onGenerate, // Ctrl+N: same as "Generate" button
                ctrl::onGenerate);

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open Reports window", e);
        }
    }
}
