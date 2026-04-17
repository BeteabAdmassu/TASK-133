package com.eaglepoint.console.ui.bed;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedBoardWindow {

    private static final Logger log = LoggerFactory.getLogger(BedBoardWindow.class);
    private static Stage stage;

    public static void show(Stage owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                BedBoardWindow.class.getResource("/fxml/bed-board.fxml"));
            VBox root = loader.load();
            BedBoardController ctrl = loader.getController();

            stage = new Stage();
            stage.initOwner(owner);
            stage.setTitle("Bed Board");

            Scene scene = new Scene(root, 1100, 750);
            scene.getStylesheets().add(
                BedBoardWindow.class.getResource("/css/application.css").toExternalForm());

            stage.setScene(scene);
            stage.setOnHidden(e -> ctrl.stopRefresh());
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open Bed Board window", e);
        }
    }
}
