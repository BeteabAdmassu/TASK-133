package com.eaglepoint.console.ui.shared;

import com.eaglepoint.console.ui.LogViewerDialog;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Global keyboard shortcut wiring for every top-level console window.
 *
 * <p>The prompt requires four shortcuts to be <em>consistently</em>
 * available across KPI Reviews, Pickup Points, Bed Board, Reports, and
 * the main shell:
 * <ul>
 *   <li>{@code Ctrl+F} — search / filter</li>
 *   <li>{@code Ctrl+N} — new record</li>
 *   <li>{@code Ctrl+E} — export</li>
 *   <li>{@code Ctrl+L} — open logs</li>
 * </ul>
 * Each window hands this helper its concrete per-shortcut action.  If a
 * shortcut doesn't apply to a particular window the caller can pass
 * {@link #notAvailable} (which renders a clear "not available here"
 * message) so the binding is always installed and the user never hits a
 * silent no-op.
 */
public final class GlobalShortcuts {

    private GlobalShortcuts() {}

    /**
     * Install the four global shortcuts on {@code scene}.  Any handler may
     * be {@code null}; null handlers are treated as "not available" and show
     * a consistent modal so users never silently press into nothing.
     */
    public static void install(Scene scene, Stage stage,
                                Runnable onFind,
                                Runnable onNew,
                                Runnable onExport,
                                Runnable onOpenLogs) {
        if (scene == null) return;
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
            or(onFind, () -> notAvailable(stage, "Ctrl+F (search)")));
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
            or(onNew, () -> notAvailable(stage, "Ctrl+N (new record)")));
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
            or(onExport, () -> notAvailable(stage, "Ctrl+E (export)")));
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
            or(onOpenLogs, () -> LogViewerDialog.show(stage)));
    }

    /**
     * Convenience overload: Ctrl+L always opens the shared log viewer if no
     * override is provided.  Caller still needs to supply the other three.
     */
    public static void install(Scene scene, Stage stage,
                                Runnable onFind,
                                Runnable onNew,
                                Runnable onExport) {
        install(scene, stage, onFind, onNew, onExport, null);
    }

    private static Runnable or(Runnable action, Runnable fallback) {
        return action != null ? action : fallback;
    }

    static void notAvailable(Stage owner, String shortcut) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("Shortcut");
        alert.setHeaderText(shortcut + " is not available on this window");
        alert.setContentText(Optional.ofNullable(owner)
            .map(Stage::getTitle).orElse("Current window")
            + " has no action bound to this shortcut.");
        alert.show();
    }
}
