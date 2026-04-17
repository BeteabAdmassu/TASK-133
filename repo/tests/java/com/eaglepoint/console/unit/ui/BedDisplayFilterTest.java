package com.eaglepoint.console.unit.ui;

import com.eaglepoint.console.ui.bed.BedDisplayFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BedDisplayFilter} — the pure grid-filter predicate used
 * by the JavaFX bed-board window.  No JavaFX runtime is required.
 */
class BedDisplayFilterTest {

    private static Map<String, Object> bed(String label, String state) {
        return Map.of("bedLabel", label, "state", state, "id", 1);
    }

    @Test
    void nullFiltersMatchEveryBed() {
        Map<String, Object> bed = bed("101-A", "AVAILABLE");
        assertTrue(BedDisplayFilter.matches(bed, null, null));
    }

    @Test
    void emptyFiltersMatchEveryBed() {
        Map<String, Object> bed = bed("101-A", "OCCUPIED");
        assertTrue(BedDisplayFilter.matches(bed, "", ""));
    }

    @Test
    void stateFilterExcludesNonMatchingBeds() {
        Map<String, Object> occupied = bed("101-A", "OCCUPIED");
        Map<String, Object> available = bed("102-A", "AVAILABLE");

        assertFalse(BedDisplayFilter.matches(occupied, "AVAILABLE", null));
        assertTrue(BedDisplayFilter.matches(available, "AVAILABLE", null));
    }

    @Test
    void textFilterMatchesSubstringCaseInsensitive() {
        Map<String, Object> bed = bed("ICU-12-A", "AVAILABLE");

        assertTrue(BedDisplayFilter.matches(bed, null, "icu"));
        assertTrue(BedDisplayFilter.matches(bed, null, "ICU"));
        assertTrue(BedDisplayFilter.matches(bed, null, "12"));
        assertFalse(BedDisplayFilter.matches(bed, null, "ward-3"));
    }

    @Test
    void bothFiltersMustMatchSimultaneously() {
        Map<String, Object> icuAvailable = bed("ICU-1", "AVAILABLE");
        Map<String, Object> icuOccupied  = bed("ICU-2", "OCCUPIED");
        Map<String, Object> wardAvail    = bed("WARD-9", "AVAILABLE");

        assertTrue(BedDisplayFilter.matches(icuAvailable, "AVAILABLE", "ICU"));
        assertFalse(BedDisplayFilter.matches(icuOccupied, "AVAILABLE", "ICU"));
        assertFalse(BedDisplayFilter.matches(wardAvail, "AVAILABLE", "ICU"));
    }

    @Test
    void nullBedIsNeverIncluded() {
        assertFalse(BedDisplayFilter.matches(null, null, null));
        assertFalse(BedDisplayFilter.matches(null, "AVAILABLE", "anything"));
    }

    @Test
    void missingLabelIsTreatedAsEmptyString() {
        Map<String, Object> incomplete = Map.of("state", "AVAILABLE", "id", 1);

        // Empty label never contains a non-empty substring
        assertFalse(BedDisplayFilter.matches(incomplete, null, "icu"));
        // But passes when no text filter is applied
        assertTrue(BedDisplayFilter.matches(incomplete, "AVAILABLE", null));
    }

    @Test
    void missingStateDefaultsToAvailable() {
        Map<String, Object> incomplete = Map.of("bedLabel", "X-1", "id", 1);

        assertTrue(BedDisplayFilter.matches(incomplete, "AVAILABLE", null));
        assertFalse(BedDisplayFilter.matches(incomplete, "OCCUPIED", null));
    }
}
