package com.eaglepoint.console.ui;

import com.eaglepoint.console.ui.bed.BedBoardWindow;
import com.eaglepoint.console.ui.evaluation.EvaluationWindow;
import com.eaglepoint.console.ui.kpi.KpiReviewWindow;
import com.eaglepoint.console.ui.pickup.PickupPointWindow;
import com.eaglepoint.console.ui.reports.ReportsWindow;
import com.eaglepoint.console.ui.shared.GlobalShortcuts;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainWindow {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    @FXML private Button btnKpiReview;
    @FXML private Button btnPickupPoints;
    @FXML private Button btnBedBoard;
    @FXML private Button btnEvaluation;
    @FXML private Button btnReports;
    @FXML private Button btnHealthPanel;
    @FXML private Button btnInbox;
    @FXML private Button btnLogout;
    @FXML private Label lblStatus;
    @FXML private Label lblUser;
    @FXML private Label lblServerStatus;

    private Stage primaryStage;
    private NotificationInbox notificationInbox;

    public static void show(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                MainWindow.class.getResource("/fxml/main.fxml"));
            BorderPane root = loader.load();
            MainWindow ctrl = loader.getController();
            ctrl.primaryStage = primaryStage;

            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(
                MainWindow.class.getResource("/css/application.css").toExternalForm());

            // Install inactivity monitor
            InactivityMonitor monitor = InactivityMonitor.getInstance();
            monitor.installOnScene(scene);
            monitor.start(LockScreenController::show);

            // Install the four global shortcuts (Ctrl+F/N/E/L).  From the
            // main shell Ctrl+N opens the most common "new" action
            // (Reports), Ctrl+E exports from the reports window, and Ctrl+F
            // focuses the tile grid — each child window re-binds these to
            // window-local handlers via GlobalShortcuts.install().
            GlobalShortcuts.install(scene, primaryStage,
                () -> ctrl.btnKpiReview.requestFocus(),
                () -> ReportsWindow.show(primaryStage),
                () -> ReportsWindow.show(primaryStage),
                () -> LogViewerDialog.show(primaryStage));

            // System tray
            SystemTrayManager tray = SystemTrayManager.getInstance();
            tray.install(LockScreenController::show, primaryStage::show);

            primaryStage.setTitle("Eagle Point Console");
            primaryStage.setScene(scene);
            primaryStage.show();

            // Minimize to tray instead of closing
            primaryStage.setOnCloseRequest(e -> {
                e.consume();
                primaryStage.hide();
            });

            // Notification inbox
            ctrl.notificationInbox = new NotificationInbox(ctrl.btnInbox);
            ctrl.notificationInbox.refreshBadge();

            // Show username
            AuthSession.getInstance().getCurrentUser().ifPresent(u ->
                ctrl.lblUser.setText(u.getDisplayName() + " (" + u.getRole() + ")")
            );

            log.info("Main window shown");
        } catch (Exception e) {
            log.error("Failed to show main window", e);
            throw new RuntimeException(e);
        }
    }

    @FXML private void openKpiReview() { KpiReviewWindow.show(primaryStage); }
    @FXML private void openPickupPoints() { PickupPointWindow.show(primaryStage); }
    @FXML private void openBedBoard() { BedBoardWindow.show(primaryStage); }
    @FXML private void openEvaluation() { EvaluationWindow.show(primaryStage); }
    @FXML private void openReports() { ReportsWindow.show(primaryStage); }
    @FXML private void openHealthPanel() { HealthPanelWindow.show(primaryStage); }

    @FXML private void openInbox() {
        if (notificationInbox != null) {
            notificationInbox.toggle(primaryStage);
        }
    }

    @FXML private void logout() {
        AuthSession.getInstance().clear();
        InactivityMonitor.getInstance().stop();
        SystemTrayManager.getInstance().remove();
        primaryStage.close();
        Platform.exit();
    }
}
