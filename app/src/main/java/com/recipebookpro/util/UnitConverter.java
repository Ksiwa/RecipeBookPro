package com.recipebookpro.util;

/**
 * Simple unit conversion utility.
 * Uses conversion factor arrays loaded from resources (conversion_constants.xml).
 */
public final class UnitConverter {

    private UnitConverter() {
    }

    /**
     * Convert an amount using the factor at the given unit index.
     *
     * @param amount    the source amount
     * @param unitIndex index into the conversion_factors array
     * @param factors   the conversion factor strings from resources
     * @return converted amount
     */
    public static double convert(double amount, int unitIndex, String[] factors) {
        if (unitIndex < 0 || unitIndex >= factors.length) {
            return amount;
        }
        try {
            double factor = Double.parseDouble(factors[unitIndex]);
            return amount * factor;
        } catch (NumberFormatException e) {
            return amount;
        }
    }

    /**
     * Format a conversion result to a readable string.
     * Shows up to 2 decimal places, strips trailing zeros.
     */
    public static String formatResult(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        String formatted = String.format("%.2f", value);
        // Remove trailing zeros after decimal point
        formatted = formatted.replaceAll("0+$", "");
        formatted = formatted.replaceAll("\\.$", "");
        return formatted;
    }

    /**
     * Converts a given amount from one string unit to another.
     * Returns -1 if conversion is not directly supported.
     */
    public static double convert(double amount, String fromUnit, String toUnit) {
        fromUnit = fromUnit.toLowerCase().trim();
        toUnit = toUnit.toLowerCase().trim();

        if (fromUnit.equals(toUnit)) return amount;

        double baseVolume = -1;
        double baseWeight = -1;

        // --- To Base Volume (ml) ---
        switch (fromUnit) {
            case "ml": baseVolume = amount; break;
            case "cup": baseVolume = amount * 240.0; break;
            case "tbsp": baseVolume = amount * 15.0; break;
            case "tsp": baseVolume = amount * 5.0; break;
        }

        // --- To Base Weight (gram) ---
        switch (fromUnit) {
            case "gram":
            case "g":
                baseWeight = amount; break;
            case "oz": baseWeight = amount * 28.3495; break;
        }

        // --- From Base Volume to Target ---
        if (baseVolume != -1) {
            switch (toUnit) {
                case "ml": return baseVolume;
                case "cup": return baseVolume / 240.0;
                case "tbsp": return baseVolume / 15.0;
                case "tsp": return baseVolume / 5.0;
            }
        }

        // --- From Base Weight to Target ---
        if (baseWeight != -1) {
            switch (toUnit) {
                case "gram":
                case "g":
                    return baseWeight;
                case "oz": return baseWeight / 28.3495;
            }
        }

        return -1; // Conversion not supported
    }
}
