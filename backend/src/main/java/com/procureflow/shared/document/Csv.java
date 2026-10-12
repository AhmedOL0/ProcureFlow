package com.procureflow.shared.document;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.procureflow.shared.web.ApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RFC-4180 CSV reader for imports: header-aware, quoted commas and quoted
 * embedded newlines handled by Jackson, headers normalized to lowercase.
 * Empty lines are skipped; a file without data rows or an over-long file is
 * a 400, never a silent partial read.
 */
public final class Csv {

    private Csv() {
    }

    public static Table parse(byte[] content) {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<Map<String, String>> rows =
                mapper.readerFor(Map.class).with(schema).readValues(content)) {
            List<Map<String, String>> records = new ArrayList<>();
            while (rows.hasNext()) {
                Map<String, String> record = rows.next();
                if (isBlankRow(record)) {
                    continue;
                }
                if (records.size() >= Upload.MAX_IMPORT_ROWS) {
                    throw ApiException.badRequest(
                            "TOO_MANY_ROWS",
                            "Import is limited to " + Upload.MAX_IMPORT_ROWS + " rows per file");
                }
                records.add(normalize(record));
            }
            if (records.isEmpty()) {
                throw ApiException.badRequest("NO_DATA_ROWS", "CSV file has no data rows");
            }
            List<String> headers = new ArrayList<>(records.get(0).keySet());
            return new Table(headers, records);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.badRequest("MALFORMED_FILE", "CSV file could not be parsed");
        }
    }

    private static Map<String, String> normalize(Map<String, String> record) {
        Map<String, String> normalized = new LinkedHashMap<>();
        record.forEach((header, value) -> normalized.put(key(header), value == null ? "" : value.trim()));
        return normalized;
    }

    private static String key(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlankRow(Map<String, String> record) {
        return record.values().stream().allMatch(value -> value == null || value.isBlank());
    }
}
