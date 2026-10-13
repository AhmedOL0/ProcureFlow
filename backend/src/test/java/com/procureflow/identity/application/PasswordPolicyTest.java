package com.procureflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.procureflow.shared.web.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsTwelveToSeventyTwoBytes() {
        PasswordPolicy.requireValid("correct-horse-123");
        PasswordPolicy.requireValid("a".repeat(72));
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.requireValid("short"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    void rejectsBeyondBcryptTruncation() {
        assertThatThrownBy(() -> PasswordPolicy.requireValid("a".repeat(73)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("72 bytes");
        // Thirty emoji fit the char count but exceed 72 bytes: the byte
        // length is what BCrypt sees, so it must refuse too.
        assertThat("\uD83D\uDE00".repeat(30).getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isGreaterThan(72);
        assertThatThrownBy(() -> PasswordPolicy.requireValid("\uD83D\uDE00".repeat(30)))
                .isInstanceOf(ApiException.class);
    }
}
