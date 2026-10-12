package com.procureflow.shared.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * xlsx writer for list exports. Cells are {@code String} (formula-guarded),
 * {@code Number} (numeric, two decimals for floating point), {@code Boolean}
 * or {@code Instant} (UTC timestamp). Sheets freeze the header row, filter
 * it, and autosize columns with a sane cap. Row counts are capped by the
 * callers ({@link #MAX_EXPORT_ROWS}); the builder renders what it is given.
 */
public final class Spreadsheet {

    /** Exported data rows per file; imports cap separately in Upload. */
    public static final int MAX_EXPORT_ROWS = 5000;

    private static final int AUTOSIZE_CAP = 50;

    private Spreadsheet() {
    }

    /** One sheet of a multi-sheet workbook. */
    public record SheetData(String name, List<String> headers, List<List<Object>> rows) {
    }

    public static byte[] book(String sheetName, List<String> headers, List<List<Object>> rows) {
        return bookMulti(List.of(new SheetData(sheetName, headers, rows)));
    }

    public static byte[] bookMulti(List<SheetData> sheets) {
        try (Workbook book = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (SheetData sheet : sheets) {
                fill(book, sheet);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Spreadsheet rendering failed", e);
        }
    }

    private static void fill(Workbook book, SheetData data) {
        Sheet sheet = book.createSheet(
                data.name() == null || data.name().isBlank() ? "Export" : data.name());
        List<String> headers = data.headers();
        List<List<Object>> rows = data.rows();
        CellStyle head = head(book);
        CellStyle decimals = decimals(book);
        CellStyle stamp = stamp(book, decimals);

        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(CellText.safe(headers.get(i)));
            cell.setCellStyle(head);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            List<Object> values = rows.get(r);
            for (int c = 0; c < headers.size(); c++) {
                write(row.createCell(c), c < values.size() ? values.get(c) : null, decimals, stamp);
            }
        }
        if (!rows.isEmpty()) {
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(0, rows.size(), 0, headers.size() - 1));
        }
        for (int c = 0; c < headers.size(); c++) {
            sheet.autoSizeColumn(c);
            if (sheet.getColumnWidth(c) > AUTOSIZE_CAP * 256) {
                sheet.setColumnWidth(c, AUTOSIZE_CAP * 256);
            }
        }
    }

    private static void write(Cell cell, Object value, CellStyle decimals, CellStyle stamp) {
        switch (value) {
            case null -> cell.setBlank();
            case String text -> cell.setCellValue(CellText.safe(text));
            case Boolean flag -> cell.setCellValue(flag);
            case Instant at -> {
                cell.setCellValue(Date.from(at));
                cell.setCellStyle(stamp);
            }
            case Double amount -> {
                cell.setCellValue(amount);
                cell.setCellStyle(decimals);
            }
            case Float amount -> {
                cell.setCellValue(amount.doubleValue());
                cell.setCellStyle(decimals);
            }
            case Number number -> cell.setCellValue(number.doubleValue());
            default -> cell.setCellValue(CellText.safe(value.toString()));
        }
    }

    private static CellStyle head(Workbook book) {
        CellStyle style = book.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = book.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private static CellStyle decimals(Workbook book) {
        CellStyle style = book.createCellStyle();
        style.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private static CellStyle stamp(Workbook book, CellStyle decimals) {
        CellStyle style = book.createCellStyle();
        style.cloneStyleFrom(decimals);
        style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return style;
    }
}
