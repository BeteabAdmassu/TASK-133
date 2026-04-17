package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.model.BedState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class BedStateMachine {
    private static final Map<BedState, Set<BedState>> ALLOWED_TRANSITIONS = new EnumMap<>(BedState.class);

    static {
        ALLOWED_TRANSITIONS.put(BedState.AVAILABLE, EnumSet.of(
            BedState.OCCUPIED, BedState.RESERVED, BedState.CLEANING, BedState.MAINTENANCE
        ));
        ALLOWED_TRANSITIONS.put(BedState.OCCUPIED, EnumSet.of(
            BedState.AVAILABLE, BedState.RESERVED, BedState.CLEANING
        ));
        ALLOWED_TRANSITIONS.put(BedState.RESERVED, EnumSet.of(
            BedState.OCCUPIED, BedState.AVAILABLE
        ));
        ALLOWED_TRANSITIONS.put(BedState.CLEANING, EnumSet.of(
            BedState.AVAILABLE, BedState.MAINTENANCE
        ));
        ALLOWED_TRANSITIONS.put(BedState.MAINTENANCE, EnumSet.of(
            BedState.AVAILABLE, BedState.OUT_OF_SERVICE
        ));
        ALLOWED_TRANSITIONS.put(BedState.OUT_OF_SERVICE, EnumSet.of(
            BedState.MAINTENANCE
        ));
    }

    public static void validateTransition(BedState from, BedState to) {
        Set<BedState> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            if (from == BedState.OCCUPIED && to == BedState.OUT_OF_SERVICE) {
                throw new ConflictException(
                    "Cannot transition bed from Occupied to Out of Service. Please check out the resident first."
                );
            }
            throw new ConflictException(
                "Cannot transition bed from " + from.name() + " to " + to.name() + ". Invalid state transition."
            );
        }
    }

    public static Set<BedState> getAllowedTransitions(BedState from) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(BedState.class));
    }
}
