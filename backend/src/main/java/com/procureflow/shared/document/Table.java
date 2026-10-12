package com.procureflow.shared.document;

import java.util.List;
import java.util.Map;

/**
 * A parsed tabular file: normalized lowercase headers plus one map per data
 * row. Both the CSV and xlsx readers produce this, so import use cases stay
 * format-agnostic.
 */
public record Table(List<String> headers, List<Map<String, String>> rows) {

    public Table {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }

    public boolean has(String header) {
        return headers.contains(header);
    }
}
