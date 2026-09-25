package com.parallax.engine.model;

/**
 * Whole-dollar US currency formatting done by hand — no Locale — so output is identical on
 * every machine. Examples: {@code format(29200)} = "$29,200", {@code format(0)} = "$0",
 * {@code format(-100)} = "−$100" (using the Unicode minus sign, matching the prototype's money()).
 */
public final class Usd {

    private Usd() {
    }

    public static String format(int dollars) {
        boolean negative = dollars < 0;
        long magnitude = Math.abs((long) dollars);
        String digits = Long.toString(magnitude);

        StringBuilder grouped = new StringBuilder();
        int counted = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.append(digits.charAt(i));
            counted++;
            if (counted % 3 == 0 && i > 0) {
                grouped.append(',');
            }
        }
        grouped.reverse();

        return (negative ? "−$" : "$") + grouped;
    }
}
