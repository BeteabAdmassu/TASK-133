package com.eaglepoint.console.ui;

import com.eaglepoint.console.service.AuthService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginDialog {

    private static final Logger log = LoggerFactory.getLogger(LoginDialog.class);

    private final AuthService authService;
    private Stage stage;
    private boolean loginSucceeded = false;

    public LoginDialog(AuthService authService) {
        this.authService = authService;
    }

    public boolean showAndWait(Stage owner) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Eagle Point Console — Login");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(24));
        grid.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Eagle Point Console");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1565c0;");

        TextField tfUsername = new TextField();
        tfUsername.setPromptText("Username");
        tfUsername.setPrefWidth(260);

        PasswordField pfPassword = new PasswordField();
        pfPassword.setPromptText("Password");

        Label lblError = new Label("");
        lblError.setStyle("-fx-text-fill: #c62828; -fx-font-size: 13px;");
        lblError.setVisible(false);

        Button btnLogin = new Button("Login");
        btnLogin.setDefaultButton(true);
        btnLogin.setPrefWidth(120);
        btnLogin.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; -fx-font-size: 14px;");

        Button btnCancel = new Button("Cancel");
        btnCancel.setPrefWidth(80);

        grid.add(new Label("Username:"), 0, 0);
        grid.add(tfUsername, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(pfPassword, 1, 1);
        grid.add(lblError, 0, 2, 2, 1);
        HBox buttons = new HBox(12, btnLogin, btnCancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        grid.add(buttons, 0, 3, 2, 1);

        VBox root = new VBox(20, titleLabel, grid);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        btnLogin.setOnAction(e -> {
            String username = tfUsername.getText().trim();
            String password = pfPassword.getText();
            if (username.isEmpty() || password.isEmpty()) {
                lblError.setText("Please enter username and password.");
                lblError.setVisible(true);
                return;
            }
            btnLogin.setDisable(true);
            lblError.setVisible(false);
            new Thread(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> result =
                        (java.util.Map<String, Object>) authService.login(username, password);
                    AuthSession.getInstance().set(
                        (com.eaglepoint.console.model.User) result.get("user"),
                        (String) result.get("token"));
                    loginSucceeded = true;
                    Platform.runLater(stage::close);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        lblError.setText("Invalid username or password.");
                        lblError.setVisible(true);
                        pfPassword.clear();
                        pfPassword.requestFocus();
                        btnLogin.setDisable(false);
                    });
                }
            }, "login-thread").start();
        });

        btnCancel.setOnAction(e -> {
            loginSucceeded = false;
            Platform.exit();
        });

        stage.setOnCloseRequest(e -> {
            loginSucceeded = false;
            Platform.exit();
        });

        Scene scene = new Scene(root, 420, 300);
        try {
            scene.getStylesheets().add(
                getClass().getResource("/css/application.css").toExternalForm());
        } catch (Exception ignored) {}
        stage.setScene(scene);
        stage.showAndWait();
        return loginSucceeded;
    }
}
