package com.eaglepoint.console.unit.ui;

import com.eaglepoint.console.ui.bed.BedBoardController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link BedBoardController} actually exposes the public
 * handler methods that {@code GlobalShortcuts.install(...)} and
 * {@code ContextMenuFactory.buildForBed(...)} bind to.
 *
 * <p>This catches the case where a rename breaks the wiring without any
 * compile-time error (the shortcut installer takes {@link Runnable}s).</p>
 */
class BedBoardControllerShortcutTest {

    @Test
    void focusSearchIsPublicVoidNoArg() {
        Method m = findPublicVoidNoArg("focusSearch");
        assertNotNull(m, "BedBoardController.focusSearch() must be public void");
    }

    @Test
    void openNewBedIsPublicVoidNoArg() {
        Method m = findPublicVoidNoArg("openNewBed");
        assertNotNull(m, "BedBoardController.openNewBed() must be public void for Ctrl+N");
    }

    @Test
    void showTransferDialogIsPublic() {
        boolean found = false;
        for (Method m : BedBoardController.class.getDeclaredMethods()) {
            if (m.getName().equals("showTransferDialog") && m.getParameterCount() == 1) {
                found = true;
                assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()),
                    "showTransferDialog must be public so ContextMenuFactory can bind it");
            }
        }
        assertTrue(found, "BedBoardController.showTransferDialog(Bed) must exist");
    }

    private Method findPublicVoidNoArg(String name) {
        try {
            Method m = BedBoardController.class.getMethod(name);
            if (m.getReturnType() == void.class) return m;
        } catch (NoSuchMethodException ignored) {}
        return null;
    }
}
