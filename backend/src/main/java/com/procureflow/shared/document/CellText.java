package com.procureflow.shared.document;

/**
 * Spreadsheet formula-injection guard. A cell starting with {@code = + - @}
 * (or tab/return, or the legacy {@code | %} triggers) executes as a formula
 * when the export opens in Excel — including hostile payloads typed into a
 * supplier name. Prefixing with the text marker {@code '} keeps the visible
 * value identical while forcing text treatment. Applied to every string the
 * builders emit, and mirrored client-side for CSV downloads.
 */
public final class CellText {

    private CellText() {
    }

    public static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '|' || first == '%'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }
}
