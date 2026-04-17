package com.eaglepoint.console.ui.bed;

import java.util.Map;

/**
 * Pure filter logic for the bed-board grid.
 *
 * <p>Extracted from {@link BedBoardController} so the filtering behaviour can be
 * unit-tested without instantiating JavaFX controls.  The method is deterministic
 * and has no dependency on JavaFX, the API, or any singleton state.</p>
 */
public final class BedDisplayFilter {

    private BedDisplayFilter() {}

    /**
     * Returns {@code true} when the given bed row should be included in the
     * rendered grid given the current filter selections.
     *
     * @param bed          a bed JSON row (as returned by {@code /api/beds})
     * @param stateFilter  state to match; {@code null} or empty means "any state"
     * @param textFilter   text the label must contain (case-insensitive);
     *                     {@code null} or empty means "any label"
     */
    public static boolean matches(Map<String, Object> bed, String stateFilter, String textFilter) {
        if (bed == null) return false;
        String state = String.valueOf(bed.getOrDefault("state", "AVAILABLE"));
        String label = String.valueOf(bed.getOrDefault("bedLabel", ""));

        if (stateFilter != null && !stateFilter.isEmpty() && !stateFilter.equals(state)) {
            return false;
        }
        if (textFilter != null && !textFilter.isEmpty()
            && !label.toLowerCase().contains(textFilter.toLowerCase())) {
            return false;
        }
        return true;
    }
}
