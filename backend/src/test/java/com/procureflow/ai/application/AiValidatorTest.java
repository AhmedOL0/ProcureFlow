package com.procureflow.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.shared.web.ApiException;
import org.junit.jupiter.api.Test;

/**
 * Prompt-injection defense without a live provider: the model is untrusted
 * input, so these pin what the validator refuses and what the system prompts
 * promise. A hostile completion can neither widen the draft shape nor smuggle
 * privileges through it — only whitelisted fields survive, everything else
 * is ignored or rejected.
 */
class AiValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hostileFieldsNeverSurviveExtraction() {
        String hostile = """
                {"title": "Laptops", "priority": "URGENT",
                 "items": [{"description": "ThinkPad", "quantity": 2, "unitPriceMinor": 89900}],
                 "approved": true, "role": "TENANT_ADMIN", "requesterId": "someone-else"}""";
        AiValidator.DraftRequest draft = AiValidator.draft(hostile, mapper);
        assertThat(draft.title()).isEqualTo("Laptops");
        assertThat(draft.items()).hasSize(1);
        // The record carries no privilege surface at all: compile-time shape,
        // not runtime filtering, is what drops the smuggled fields.
        assertThat(draft.getClass().getRecordComponents()).extracting("name")
                .containsExactlyInAnyOrder("title", "priority", "items");
    }

    @Test
    void malformedOrAbusiveDraftsAreRejected() {
        assertThatThrownBy(() -> AiValidator.draft("not json", mapper))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiValidator.draft("[1,2]", mapper))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiValidator.draft("{\"title\":\"\",\"items\":[]}", mapper))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiValidator.draft(
                        "{\"title\":\"T\",\"items\":[{\"description\":\"D\",\"quantity\":0,\"unitPriceMinor\":-5}]}",
                        mapper))
                .isInstanceOf(ApiException.class);
        StringBuilder oversized = new StringBuilder("{\"title\":\"T\",\"items\":[");
        for (int i = 0; i < 21; i++) {
            oversized.append("{\"description\":\"D\",\"quantity\":1,\"unitPriceMinor\":1},");
        }
        oversized.setLength(oversized.length() - 1);
        assertThatThrownBy(() -> AiValidator.draft(oversized + "]}", mapper))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void oversizedQuestionsNeverReachTheProvider() {
        assertThatThrownBy(() -> AiValidator.question("x".repeat(2001)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2000");
        assertThat(AiValidator.question("  What did we order?  ")).isEqualTo("What did we order?");
    }

    @Test
    void oversizedAnswersAreRejected() {
        assertThatThrownBy(() -> AiValidator.answer("x".repeat(4001)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void systemPromptsKeepTheirGroundingConstraints() {
        // If anyone dilutes these instructions, the copilot starts inventing
        // numbers or emitting free-form drafts the validator cannot trust.
        assertThat(Prompts.CHAT_SYSTEM).contains("ONLY").contains("never invent numbers");
        assertThat(Prompts.EXTRACT_SYSTEM).contains("STRICT").contains("JSON only");
        assertThat(Prompts.EXPLAIN_SYSTEM).contains("no invented numbers");
    }
}
