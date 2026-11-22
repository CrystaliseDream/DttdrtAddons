package com.dttdrt.addons.util;

import java.text.DecimalFormat;

public class NumberFormatUtils {
    private static final DecimalFormat COMMA_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat EXACT_DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat SHORT_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("#,##0.0");

    /**
     * Formats coin numbers with commas, e.g. 1,250,000
     */
    public static String formatWithCommas(long number) {
        return COMMA_FORMAT.format(number);
    }

    public static String formatWithCommas(double number) {
        return COMMA_FORMAT.format(number);
    }

    /**
     * Formats exact prices with commas and up to 2 decimal places when fractional,
     * e.g. 495,000,000 or 14,976,457.8
     */
    public static String formatExact(double number) {
        if (Math.abs(number - Math.rint(number)) < 0.005) {
            return COMMA_FORMAT.format(Math.round(number));
        }
        return EXACT_DECIMAL_FORMAT.format(number);
    }

    /**
     * Formats coin numbers with short suffixes, e.g. 1.25M, 1.15B, 45.2K
     */
    public static String formatCompact(double number) {
        if (number < 0) {
            return "-" + formatCompact(-number);
        }
        if (number >= 1_000_000_000) {
            return ONE_DECIMAL.format(number / 1_000_000_000.0) + "B";
        }
        if (number >= 1_000_000) {
            return ONE_DECIMAL.format(number / 1_000_000.0) + "M";
        }
        if (number >= 1_000) {
            return ONE_DECIMAL.format(number / 1_000.0) + "K";
        }
        return SHORT_FORMAT.format(number);
    }

    /**
     * Parse human input like "1.2m" or "500k" or "1.5b" or "1000000" into a double.
     */
    public static double parsePriceInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return 0.0;
        }
        String clean = input.trim().toLowerCase().replace(",", "");
        try {
            if (clean.endsWith("b")) {
                return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000_000L;
            } else if (clean.endsWith("m")) {
                return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000L;
            } else if (clean.endsWith("k")) {
                return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000L;
            }
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

