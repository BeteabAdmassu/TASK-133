package com.eaglepoint.console;

import com.eaglepoint.console.api.ApiServer;
import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.config.LoggingConfig;
import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.scheduler.JobScheduler;
import com.eaglepoint.console.service.AuthService;
import com.eaglepoint.console.ui.LockScreenController;
import com.eaglepoint.console.ui.LoginDialog;
import com.eaglepoint.console.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        // Check if headless mode is requested
        boolean headless = "true".equalsIgnoreCase(System.getProperty("app.headless"))
            || "true".equalsIgnoreCase(System.getenv("APP_HEADLESS"));
        if (headless) {
            HeadlessEntryPoint.run();
            return;
        }
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            LoggingConfig.init();
            AppConfig.init();
            SecurityConfig.init();
            DatabaseConfig.init();

            int port = AppConfig.getInstance().getApiPort();
            ApiServer.start(port);
            JobScheduler.getInstance().start();

            // Wire AuthService into LockScreen
            var ds = DatabaseConfig.getInstance().getDataSource();
            var userRepo = new com.eaglepoint.console.repository.UserRepository(ds);
            var tokenRepo = new com.eaglepoint.console.repository.ApiTokenRepository(ds);
            var tokenService = new com.eaglepoint.console.security.TokenService();
            var authService = new AuthService(userRepo, tokenRepo, tokenService);
            LockScreenController.setAuthService(authService);

            // Show login dialog
            LoginDialog loginDialog = new LoginDialog(authService);
            boolean loggedIn = loginDialog.showAndWait(primaryStage);
            if (!loggedIn) {
                return;
            }

            // Show main window
            MainWindow.show(primaryStage);

            primaryStage.setOnCloseRequest(e -> {
                try {
                    ApiServer.stop();
                    JobScheduler.getInstance().shutdown();
                } catch (Exception ex) {
                    log.error("Error during shutdown", ex);
                }
            });

        } catch (Exception e) {
            log.error("Application startup failed", e);
            throw e;
        }
    }
}
