package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfTest {

    @Test
    void rendersTitleMetaAndTablesAsExtractableText() throws Exception {
        byte[] content = Pdf.document(
                "Purchase order 255f1a2a",
                List.of("Workspace: demo", "Status: SENT"),
                List.of(new Pdf.Section(
                        "Lines",
                        new Pdf.Table(
                                List.of("Description", "Qty", "Total"),
                                List.of(60, 15, 25),
                                List.of(List.of("Spare monitors", "4", "MAD 4,800.00")),
                                Set.of(1, 2)))),
                "ProcureFlow export",
                Instant.parse("2026-10-12T13:00:00Z"));
        assertThat(new String(content, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(content)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text)
                    .contains("Purchase order 255f1a2a")
                    .contains("Spare monitors")
                    .contains("MAD 4,800.00")
                    .contains("Page 1 of 1");
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void paginatesLongTablesWithNumberedFooters() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            rows.add(List.of("Item " + i, "1", "MAD 100.00"));
        }
        byte[] content = Pdf.document(
                "Big export",
                List.of(),
                List.of(new Pdf.Section(null, new Pdf.Table(List.of("A", "B", "C"), List.of(60, 15, 25), rows, Set.of()))),
                "ProcureFlow export",
                Instant.EPOCH);
        try (PDDocument doc = Loader.loadPDF(content)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Page 2 of " + doc.getNumberOfPages());
        }
    }
}
