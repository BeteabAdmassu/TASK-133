package com.eaglepoint.console.ui;

import com.eaglepoint.console.service.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockScreenController {

    private static final Logger log = LoggerFactory.getLogger(LockScreenController.class);

    @FXML private Label lblUsername;
    @FXML private PasswordField pfPassword;
    @FXML private Label lblError;

    private static Stage lockStage;
    private static AuthService authService;

    public static void setAuthService(AuthService service) {
        authService = service;
    }

    public static void show() {
        if (lockStage != null && lockStage.isShowing()) return;
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                    LockScreenController.class.getResource("/fxml/lock-screen.fxml"));
                StackPane root = loader.load();
                LockScreenController ctrl = loader.getController();
                AuthSession session = AuthSession.getInstance();
                ctrl.lblUsername.setText(session.getCurrentUser()
                    .map(u -> "Logged in as: " + u.getUsername()).orElse(""));

                lockStage = new Stage(StageStyle.UNDECORATED);
                lockStage.initModality(Modality.APPLICATION_MODAL);
                lockStage.setTitle("Session Locked");
                Scene scene = new Scene(root);
                scene.getStylesheets().add(
                    LockScreenController.class.getResource("/css/application.css").toExternalForm());
                lockStage.setScene(scene);
                lockStage.setMaximized(true);
                lockStage.show();
                ctrl.pfPassword.requestFocus();
                log.info("Lock screen displayed");
            } catch (Exception e) {
                log.error("Failed to show lock screen", e);
            }
        });
    }

    @FXML
    private void onUnlock() {
        String password = pfPassword.getText();
        if (password == null || password.isEmpty()) {
            lblError.setText("Please enter your password.");
            lblError.setVisible(true);
            return;
        }
        AuthSession session = AuthSession.getInstance();
        String username = session.getCurrentUser()
            .map(u -> u.getUsername()).orElse(null);
        if (username == null) {
            lblError.setText("No active session.");
            lblError.setVisible(true);
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            var result = (java.util.Map<String, Object>) authService.login(username, password);
            session.set((com.eaglepoint.console.model.User) result.get("user"),
                        (String) result.get("token"));
            dismiss();
        } catch (Exception e) {
            lblError.setText("Incorrect password. Please try again.");
            lblError.setVisible(true);
            pfPassword.clear();
            pfPassword.requestFocus();
        }
    }

    private static void dismiss() {
        if (lockStage != null) {
            lockStage.close();
            lockStage = null;
            log.info("Lock screen dismissed");
        }
    }
}
