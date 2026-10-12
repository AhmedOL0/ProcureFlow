package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTextTest {

    @Test
    void formatsMinorUnitsWithGrouping() {
        assertThat(MoneyText.of(150000, "MAD")).isEqualTo("MAD 1,500.00");
        assertThat(MoneyText.of(5, "EUR")).isEqualTo("EUR 0.05");
        assertThat(MoneyText.of(0, "MAD")).isEqualTo("MAD 0.00");
    }

    @Test
    void formatsNegativeAmountsWithLeadingSign() {
        assertThat(MoneyText.of(-150050, "MAD")).isEqualTo("MAD -1,500.50");
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(() -> MoneyText.of(100, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
