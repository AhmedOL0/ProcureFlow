package com.procureflow.shared.document;

import java.util.Locale;

/**
 * Money as humans read it in documents: {@code MAD 1,500.00}. Amounts stay
 * integer minor units everywhere else; only this edge formats them.
 */
public final class MoneyText {

    private MoneyText() {
    }

    public static String of(long minor, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        long major = minor / 100;
        long cents = Math.abs(minor % 100);
        // Explicit locale: grouping separators must not follow the server default.
        return String.format(
                Locale.US,
                "%s %s%,d.%02d",
                currency.trim(),
                minor < 0 ? "-" : "",
                Math.abs(major),
                cents);
    }
}
