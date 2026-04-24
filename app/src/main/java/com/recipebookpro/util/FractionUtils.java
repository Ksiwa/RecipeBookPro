package com.recipebookpro.util;

/**
 * Utility for converting decimal amounts to user-friendly fraction strings.
 * Used in ingredient scaling (e.g. 0.5 → "1/2", 1.333 → "1 1/3").
 */
public final class FractionUtils {

    private static final int[][] COMMON_FRACTIONS = {
            {1, 8}, {1, 4}, {1, 3}, {3, 8}, {1, 2},
            {5, 8}, {2, 3}, {3, 4}, {7, 8}
    };
    private static final double TOLERANCE = 0.04;

    private FractionUtils() {
    }

    /**
     * Convert a double value to a human-readable fraction string.
     * Examples: 0.5 → "1/2", 1.5 → "1 1/2", 0.333 → "1/3", 2.0 → "2"
     */
    public static String toFractionString(double value) {
        if (value <= 0) return "0";

        int wholePart = (int) value;
        double fractionalPart = value - wholePart;

        if (fractionalPart < TOLERANCE) {
            return String.valueOf(wholePart);
        }

        if (1.0 - fractionalPart < TOLERANCE) {
            return String.valueOf(wholePart + 1);
        }

        String fractionStr = matchFraction(fractionalPart);

        if (wholePart == 0) {
            return fractionStr;
        }
        return wholePart + " " + fractionStr;
    }

    private static String matchFraction(double decimal) {
        for (int[] fraction : COMMON_FRACTIONS) {
            double fractionValue = (double) fraction[0] / fraction[1];
            if (Math.abs(decimal - fractionValue) < TOLERANCE) {
                return fraction[0] + "/" + fraction[1];
            }
        }
        // No common fraction match — round to 1 decimal
        String formatted = String.format("%.1f", decimal);
        // Remove trailing ".0"
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }
        return formatted;
    }

    /**
     * Try to parse a numeric amount from a string (supports decimals and simple fractions).
     * Returns 0.0 if unparseable.
     */
    public static double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return 0.0;
        }
        String s = amountStr.trim();
        try {
            // Simple fraction like "1/2"
            if (s.contains("/")) {
                String[] parts = s.split("/");
                if (parts.length == 2) {
                    return Double.parseDouble(parts[0].trim()) / Double.parseDouble(parts[1].trim());
                }
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
