package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SpreadsheetTest {

    @Test
    void writesGuardedCellsBehindAHeaderRow() throws Exception {
        byte[] content = Spreadsheet.book(
                "Suppliers",
                List.of("name", "total"),
                List.of(List.of("=cmd|'/c calc'!A0", 1500.5)));
        try (Workbook book = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            var sheet = book.getSheet("Suppliers");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("name");
            // Formula trigger neutralized with the text marker.
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).startsWith("'");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1500.5);
        }
    }

    @Test
    void startsWithTheZipMagic() {
        byte[] content = Spreadsheet.book("S", List.of("a"), List.of());
        assertThat(content[0]).isEqualTo((byte) 0x50);
        assertThat(content[1]).isEqualTo((byte) 0x4B);
    }
}
