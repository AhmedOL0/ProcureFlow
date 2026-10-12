package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.procureflow.shared.web.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class XlsxTest {

    @Test
    void roundTripsThroughTheExportBuilder() {
        byte[] content = Spreadsheet.book(
                "Suppliers",
                List.of("name", "email"),
                List.of(List.of("Acme Parts", "acme@test.com"), List.of(42, true)));
        Table table = Xlsx.read(content);
        assertThat(table.headers()).containsExactly("name", "email");
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0)).containsEntry("name", "Acme Parts");
        assertThat(table.rows().get(1)).containsEntry("name", "42");
    }

    @Test
    void rejectsCorruptWorkbooks() {
        assertThatThrownBy(() -> Xlsx.read(new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00}))
                .isInstanceOf(ApiException.class);
    }
}
