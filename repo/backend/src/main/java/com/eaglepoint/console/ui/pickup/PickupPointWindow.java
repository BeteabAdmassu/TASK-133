package com.eaglepoint.console.ui.pickup;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PickupPointWindow {

    private static final Logger log = LoggerFactory.getLogger(PickupPointWindow.class);
    private static Stage stage;

    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                PickupPointWindow.class.getResource("/fxml/pickup-points.fxml"));
            VBox root = loader.load();
            PickupPointController ctrl = loader.getController();

            stage = new Stage();
            stage.initOwner(owner);
            stage.setTitle("Pickup Points");
            ctrl.setOwnerStage(stage);

            Scene scene = new Scene(root, 1000, 700);
            scene.getStylesheets().add(
                PickupPointWindow.class.getResource("/css/application.css").toExternalForm());

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open Pickup Points window", e);
        }
    }
}
