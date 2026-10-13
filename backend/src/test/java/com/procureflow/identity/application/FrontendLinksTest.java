package com.procureflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FrontendLinksTest {

    @Test
    void acceptsAbsoluteHttpUrlsAndStripsSlashes() {
        assertThat(FrontendLinks.base("https://app.example.com///")).isEqualTo("https://app.example.com");
        assertThat(FrontendLinks.base("http://localhost:4200")).isEqualTo("http://localhost:4200");
    }

    @Test
    void refusesNonHttpTargets() {
        assertThatThrownBy(() -> FrontendLinks.base("notaurl")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FrontendLinks.base("javascript:alert(1)")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FrontendLinks.base(null)).isInstanceOf(IllegalStateException.class);
    }
}
