package com.procureflow.shared.document;

import com.procureflow.shared.web.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * xlsx reader for imports: first sheet only, first non-blank row is the
 * header (normalized to lowercase), numerics and dates formatted as text —
 * import columns are all textual. Blank rows are skipped; a corrupt
 * workbook or an over-long sheet is a 400.
 */
public final class Xlsx {

    private Xlsx() {
    }

    public static Table read(byte[] content) {
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = book.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<String> headers = List.of();
            List<Map<String, String>> records = new ArrayList<>();
            boolean headed = false;
            for (Row row : sheet) {
                List<String> cells = cellsOf(row, formatter);
                if (cells.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (!headed) {
                    headers = cells.stream().map(Xlsx::key).toList();
                    headed = true;
                    continue;
                }
                if (records.size() >= Upload.MAX_IMPORT_ROWS) {
                    throw ApiException.badRequest(
                            "TOO_MANY_ROWS",
                            "Import is limited to " + Upload.MAX_IMPORT_ROWS + " rows per file");
                }
                records.add(zip(headers, cells));
            }
            if (!headed) {
                throw ApiException.badRequest("NO_DATA_ROWS", "Workbook has no header row");
            }
            return new Table(headers, records);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("CORRUPT_FILE", "Workbook could not be parsed");
        }
    }

    private static List<String> cellsOf(Row row, DataFormatter formatter) {
        int width = row.getLastCellNum();
        List<String> cells = new ArrayList<>(Math.max(width, 0));
        for (int i = 0; i < width; i++) {
            String text = formatter.formatCellValue(row.getCell(i));
            cells.add(text == null ? "" : text.trim());
        }
        return cells;
    }

    private static Map<String, String> zip(List<String> headers, List<String> cells) {
        Map<String, String> record = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            record.put(headers.get(i), i < cells.size() ? cells.get(i) : "");
        }
        return record;
    }

    private static String key(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }
}
