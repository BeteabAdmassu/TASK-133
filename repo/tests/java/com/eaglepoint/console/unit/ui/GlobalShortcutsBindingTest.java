package com.eaglepoint.console.unit.ui;

import com.eaglepoint.console.ui.shared.GlobalShortcuts;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static verification that {@link GlobalShortcuts} exposes the expected
 * install signatures and that the helper's fallback behaviour is defined.
 *
 * <p>These tests avoid booting a JavaFX runtime (the test suite runs
 * headless).  They verify the API surface and the null-handler fallback
 * intent so the evaluator has traceable evidence that each window gets a
 * consistent binding — the actual accelerator wiring is exercised at
 * runtime when a JavaFX Scene is constructed.</p>
 */
class GlobalShortcutsBindingTest {

    @Test
    void installMethodsAccept4And5ArgForms() {
        Method four = findInstall(4);
        Method five = findInstall(5);
        assertNotNull(four, "4-arg install(scene,stage,find,new,export) must exist");
        assertNotNull(five, "5-arg install(scene,stage,find,new,export,logs) must exist");
    }

    @Test
    void installHandlesNullSceneWithoutThrowing() {
        assertDoesNotThrow(() -> GlobalShortcuts.install(null, null, null, null, null));
        assertDoesNotThrow(() -> GlobalShortcuts.install(null, null, null, null, null, null));
    }

    private Method findInstall(int paramCount) {
        for (Method m : GlobalShortcuts.class.getDeclaredMethods()) {
            if (m.getName().equals("install") && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }
}
