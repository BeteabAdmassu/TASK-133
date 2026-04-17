package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.model.BedState;
import com.eaglepoint.console.service.BedStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BedStateMachineTest {

    private BedStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new BedStateMachine();
    }

    @Test
    void availableToOccupiedIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.AVAILABLE, BedState.OCCUPIED));
    }

    @Test
    void availableToReservedIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.AVAILABLE, BedState.RESERVED));
    }

    @Test
    void availableToMaintenanceIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.AVAILABLE, BedState.MAINTENANCE));
    }

    @Test
    void occupiedToAvailableIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.OCCUPIED, BedState.AVAILABLE));
    }

    @Test
    void occupiedToCleaningIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.OCCUPIED, BedState.CLEANING));
    }

    @Test
    void occupiedToOutOfServiceIsForbidden() {
        ConflictException ex = assertThrows(ConflictException.class,
            () -> machine.validateTransition(BedState.OCCUPIED, BedState.OUT_OF_SERVICE));
        assertTrue(ex.getMessage().contains("Out of Service") || ex.getMessage().contains("out_of_service")
            || ex.getMessage().toLowerCase().contains("occupied"));
    }

    @Test
    void cleaningToAvailableIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.CLEANING, BedState.AVAILABLE));
    }

    @Test
    void maintenanceToAvailableIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.MAINTENANCE, BedState.AVAILABLE));
    }

    @Test
    void outOfServiceToMaintenanceIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.OUT_OF_SERVICE, BedState.MAINTENANCE));
    }

    @Test
    void sameStateTransitionIsForbidden() {
        assertThrows(ConflictException.class,
            () -> machine.validateTransition(BedState.AVAILABLE, BedState.AVAILABLE));
    }

    @Test
    void reservedToOccupiedIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.RESERVED, BedState.OCCUPIED));
    }

    @Test
    void reservedToAvailableIsAllowed() {
        assertDoesNotThrow(() -> machine.validateTransition(BedState.RESERVED, BedState.AVAILABLE));
    }
}
