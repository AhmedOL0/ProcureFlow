package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.procureflow.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UploadTest {

    @Test
    void acceptsXlsxByMagicNotExtensionCase() {
        byte[] content = Spreadsheet.book("S", List.of("a"), List.of());
        assertThat(Upload.inspect("Suppliers.XLSX", content)).isEqualTo(Upload.FileKind.XLSX);
    }

    @Test
    void acceptsCsvText() {
        byte[] content = "name\nAcme\n".getBytes(StandardCharsets.UTF_8);
        assertThat(Upload.inspect("suppliers.csv", content)).isEqualTo(Upload.FileKind.CSV);
    }

    @Test
    void rejectsEmptyOversizedAndForeignFiles() {
        assertThatThrownBy(() -> Upload.inspect("s.csv", new byte[0]))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> Upload.inspect("s.csv", new byte[(int) Upload.MAX_BYTES + 1]))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("5 MB");
        assertThatThrownBy(() -> Upload.inspect("s.pdf", new byte[] {1, 2, 3}))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only .xlsx and .csv");
    }

    @Test
    void rejectsMasqueradingContent() {
        // Zip magic missing despite the extension.
        assertThatThrownBy(() -> Upload.inspect("s.xlsx", "name\nAcme\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class);
        // NUL byte: binary wearing a .csv name.
        assertThatThrownBy(() -> Upload.inspect("s.csv", new byte[] {'a', 0, 'b'}))
                .isInstanceOf(ApiException.class);
    }
}
