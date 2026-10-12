package com.procureflow.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilenamesTest {

    @Test
    void stampsAsciiSafeNames() {
        String name = Filenames.xlsx("purchase-orders", "acme");
        assertThat(name).startsWith("purchase-orders-acme-").endsWith(".xlsx");
        assertThat(name).matches("[a-z0-9.\\-]+");
    }

    @Test
    void fallsBackOnHostileInput() {
        assertThat(Filenames.pdf("../../evil", null)).matches("[a-z0-9.\\-]+");
    }
}
