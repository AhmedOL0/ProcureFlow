package com.procureflow.shared.document;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Printable-document kit (purchase orders, invoices, spend reports): A4
 * pages, title block, optional headed tables with wrapping and pagination,
 * and a {@code Page i of N} footer pass. Plain monochrome business layout —
 * documents are paperwork, not marketing. Text is ASCII-folded where the
 * built-in Helvetica lacks a glyph (e.g. the em dash) so nothing renders
 * as tofu.
 */
public final class Pdf {

    /** Headed or bare table; widths are percentages of the usable width. */
    public record Table(
            List<String> headers, List<Integer> widths, List<List<String>> rows, Set<Integer> numeric) {
    }

    public record Section(String heading, Table table) {
    }

    private static final float MARGIN = 48;
    private static final float TITLE_SIZE = 16;
    private static final float HEADING_SIZE = 12;
    private static final float BODY_SIZE = 10;
    private static final float SMALL_SIZE = 8;
    private static final float LEADING = 13;
    private static final float CELL_PAD = 4;

    private static final Color INK = new Color(0x1F2937);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Color RULE = new Color(0xD1D5DB);
    private static final Color SHADE = new Color(0xF3F4F6);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private Pdf() {
    }

    public static byte[] document(
            String title, List<String> meta, List<Section> sections, String footerNote, Instant generatedAt) {
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont plain = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            Cursor cursor = new Cursor(doc);
            cursor.writeTitle(fold(title), bold);
            for (String line : meta) {
                cursor.writeMeta(fold(line), plain);
            }
            cursor.gap(6);
            for (Section section : sections) {
                if (section.heading() != null && !section.heading().isBlank()) {
                    cursor.writeHeading(fold(section.heading()), bold);
                }
                cursor.writeTable(section.table(), plain, bold);
            }
            cursor.close();
            footer(doc, footerNote, generatedAt, plain);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    /** Helvetica has no em dash or middot; fold the few we emit. */
    static String fold(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u2014', '-').replace('\u00B7', '-').replace('\u2026', '.');
    }

    /** UTC stamp for document meta lines, e.g. {@code 2026-10-12 13:00 UTC}. */
    public static String stamp(Instant at) {
        return at == null ? "-" : STAMP.format(at);
    }

    private static void footer(
            PDDocument doc, String footerNote, Instant generatedAt, PDFont font) throws IOException {
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = doc.getPage(i);
            try (PDPageContentStream stream =
                    new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setFont(font, SMALL_SIZE);
                stream.setNonStrokingColor(MUTED);
                float width = page.getMediaBox().getWidth();
                String left = "Page " + (i + 1) + " of " + total;
                String right = fold(footerNote) + "  |  Generated " + STAMP.format(generatedAt);
                stream.beginText();
                stream.newLineAtOffset(MARGIN, MARGIN - 18);
                stream.showText(left);
                stream.endText();
                float rightWidth = font.getStringWidth(right) / 1000 * SMALL_SIZE;
                stream.beginText();
                stream.newLineAtOffset(width - MARGIN - rightWidth, MARGIN - 18);
                stream.showText(right);
                stream.endText();
            }
        }
    }

    /** Draw cursor with automatic pagination. */
    private static final class Cursor {
        private final PDDocument doc;
        private final float usable;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument doc) throws IOException {
            this.doc = doc;
            this.usable = PDRectangle.A4.getWidth() - 2 * MARGIN;
            newPage();
        }

        void writeTitle(String title, PDFont font) throws IOException {
            ensure(2 * LEADING + 4);
            text(title, MARGIN, y, font, TITLE_SIZE, INK);
            y -= 2 * LEADING + 2;
            rule();
        }

        void writeMeta(String line, PDFont font) throws IOException {
            ensure(LEADING);
            text(line, MARGIN, y, font, BODY_SIZE, MUTED);
            y -= LEADING;
        }

        void gap(float points) {
            y -= points;
        }

        void writeHeading(String heading, PDFont font) throws IOException {
            ensure(2 * LEADING);
            y -= 4;
            text(heading, MARGIN, y, font, HEADING_SIZE, INK);
            y -= LEADING + 6;
        }

        void writeTable(Table table, PDFont plain, PDFont bold) throws IOException {
            float[] edges = edgesOf(table.widths());
            if (!table.headers().isEmpty()) {
                List<String> heads = table.headers().stream().map(Pdf::fold).toList();
                float height = rowHeight(heads, edges, bold) + 2 * CELL_PAD;
                ensure(height);
                shadeRow(height);
                drawRow(heads, edges, bold, INK, Set.of());
                rule();
            }
            for (List<String> raw : table.rows()) {
                List<String> cells = raw.stream().map(Pdf::fold).toList();
                float height = rowHeight(cells, edges, plain) + 2 * CELL_PAD;
                ensure(height);
                drawRow(cells, edges, plain, INK, table.numeric());
                rule();
            }
            y -= 8;
        }

        private float[] edgesOf(List<Integer> widths) {
            int total = widths.stream().mapToInt(Integer::intValue).sum();
            float[] edges = new float[widths.size() + 1];
            edges[0] = MARGIN;
            for (int i = 0; i < widths.size(); i++) {
                edges[i + 1] = edges[i] + usable * widths.get(i) / total;
            }
            return edges;
        }

        private float rowHeight(List<String> cells, float[] edges, PDFont font) throws IOException {
            int lines = 1;
            for (int i = 0; i < cells.size() && i + 1 < edges.length; i++) {
                lines = Math.max(lines, wrap(cells.get(i), font, BODY_SIZE, edges[i + 1] - edges[i] - 2 * CELL_PAD)
                        .size());
            }
            return lines * LEADING;
        }

        private void drawRow(List<String> cells, float[] edges, PDFont font, Color color, Set<Integer> numeric)
                throws IOException {
            float top = y - CELL_PAD;
            for (int i = 0; i < cells.size() && i + 1 < edges.length; i++) {
                List<String> lines =
                        wrap(cells.get(i), font, BODY_SIZE, edges[i + 1] - edges[i] - 2 * CELL_PAD);
                for (int l = 0; l < lines.size(); l++) {
                    String line = lines.get(l);
                    float lineWidth = font.getStringWidth(line) / 1000 * BODY_SIZE;
                    float x = numeric.contains(i) ? edges[i + 1] - CELL_PAD - lineWidth : edges[i] + CELL_PAD;
                    // Guard absurd widths (empty strings measure 0, never negative).
                    x = Math.max(x, edges[i]);
                    text(line, x, top - (l + 1) * LEADING + 3, font, BODY_SIZE, color);
                }
            }
            y -= rowHeight(cells, edges, font) + 2 * CELL_PAD;
        }

        private void shadeRow(float height) throws IOException {
            stream.setNonStrokingColor(SHADE);
            stream.addRect(MARGIN, y - height, usable, height);
            stream.fill();
        }

        private void rule() throws IOException {
            stream.setStrokingColor(RULE);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + usable, y);
            stream.stroke();
            y -= 4;
        }

        private void text(String line, float x, float y, PDFont font, float size, Color color)
                throws IOException {
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.beginText();
            stream.newLineAtOffset(x, y);
            stream.showText(line);
            stream.endText();
        }

        private void ensure(float height) throws IOException {
            if (y - height < MARGIN + 20) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }

    static List<String> wrap(String text, PDFont font, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size <= width || line.isEmpty()) {
                    line = new StringBuilder(candidate);
                } else {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            // A single word wider than the column is hard-split so it can
            // never overflow into the next column.
            while (font.getStringWidth(line.toString()) / 1000 * size > width && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && font.getStringWidth(line.substring(0, cut)) / 1000 * size > width) {
                    cut--;
                }
                lines.add(line.substring(0, cut));
                line = new StringBuilder(line.substring(cut));
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
