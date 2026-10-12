package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CellTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"=cmd|'/c calc'!A0", "+123", "-5", "@mention", "|pipe", "%mod", "\tindented"})
    void prefixesFormulaTriggersWithTextMarker(String hostile) {
        assertThat(CellText.safe(hostile)).startsWith("'").contains(hostile);
    }

    @Test
    void leavesPlainTextUntouched() {
        assertThat(CellText.safe("Bechtle Demo AG")).isEqualTo("Bechtle Demo AG");
        assertThat(CellText.safe("")).isEqualTo("");
        assertThat(CellText.safe(null)).isEqualTo("");
    }
}
