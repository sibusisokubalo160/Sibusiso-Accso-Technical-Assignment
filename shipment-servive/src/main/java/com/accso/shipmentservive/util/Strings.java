package com.accso.shipmentservive.util;

/**
 * Small string helpers shared across validation and duplicate-comparison logic.
 */
public final class Strings {

    private Strings() {
    }

    /**
     * Treats {@code null} and blank/whitespace-only strings the same way: as "not provided".
     * Used both for validating required fields and for comparing an incoming duplicate's
     * payload against the originally stored event.
     */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
