package com.eaglepoint.console.ui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);
    private static SystemTrayManager instance;
    private TrayIcon trayIcon;

    private SystemTrayManager() {}

    public static synchronized SystemTrayManager getInstance() {
        if (instance == null) {
            instance = new SystemTrayManager();
        }
        return instance;
    }

    public void install(Runnable onLockCallback, Runnable onOpenCallback) {
        if (!SystemTray.isSupported()) {
            log.warn("System tray not supported on this platform");
            return;
        }

        // Run AWT operations on the AWT event thread
        EventQueue.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();

                // Create a simple icon
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setColor(java.awt.Color.BLUE);
                g.fillOval(0, 0, 15, 15);
                g.dispose();

                PopupMenu popup = new PopupMenu();

                MenuItem openItem = new MenuItem("Open");
                openItem.addActionListener(e -> Platform.runLater(onOpenCallback));

                MenuItem lockItem = new MenuItem("Lock");
                lockItem.addActionListener(e -> Platform.runLater(onLockCallback));

                MenuItem exitItem = new MenuItem("Exit");
                exitItem.addActionListener(e -> Platform.runLater(Platform::exit));

                popup.add(openItem);
                popup.add(lockItem);
                popup.addSeparator();
                popup.add(exitItem);

                trayIcon = new TrayIcon(img, "Eagle Point Console", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> Platform.runLater(onOpenCallback));

                tray.add(trayIcon);
                log.info("System tray icon installed");
            } catch (Exception e) {
                log.error("Failed to install system tray icon", e);
            }
        });
    }

    public void remove() {
        if (trayIcon != null && SystemTray.isSupported()) {
            EventQueue.invokeLater(() -> {
                SystemTray.getSystemTray().remove(trayIcon);
                trayIcon = null;
            });
        }
    }

    public void showMessage(String title, String message) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() ->
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO));
        }
    }
}
