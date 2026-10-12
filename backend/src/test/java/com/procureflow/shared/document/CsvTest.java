package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.procureflow.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void parsesHeadersAndQuotedCells() {
        byte[] content = """
                name,email,address
                "Acme, Inc",acme@test.com,"12 Main St, Suite 3"
                Bechtle,,
                """.getBytes(StandardCharsets.UTF_8);
        Table table = Csv.parse(content);
        assertThat(table.headers()).containsExactly("name", "email", "address");
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0)).containsEntry("name", "Acme, Inc");
        assertThat(table.rows().get(1)).containsEntry("email", "");
    }

    @Test
    void skipsBlankLinesAndTrimsValues() {
        byte[] content = "name\n\n  Padded  \n".getBytes(StandardCharsets.UTF_8);
        Table table = Csv.parse(content);
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().get(0)).containsEntry("name", "Padded");
    }

    @Test
    void rejectsFilesWithoutDataRows() {
        byte[] content = "name,email\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Csv.parse(content))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no data rows");
    }

    @Test
    void rejectsUnparseableContent() {
        byte[] content = "\"unterminated".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Csv.parse(content)).isInstanceOf(ApiException.class);
    }

    @Test
    void capsRowsInsteadOfReadingForever() {
        StringBuilder csv = new StringBuilder("name\n");
        for (int i = 0; i <= Upload.MAX_IMPORT_ROWS; i++) {
            csv.append("Supplier ").append(i).append('\n');
        }
        assertThatThrownBy(() -> Csv.parse(csv.toString().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("500 rows");
    }
}
