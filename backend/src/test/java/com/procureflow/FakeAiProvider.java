package com.procureflow;

import com.procureflow.ai.application.AiCompletionRequest;
import com.procureflow.ai.application.AiCompletionResult;
import com.procureflow.ai.application.AiProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Test double for the AI provider: deterministic answers per feature, real
 * token accounting, never any network. Lives in test sources so production
 * still boots with zero provider beans unless {@code AI_ENABLED=true}.
 * Backs off when AI is enabled so exactly one provider bean exists in every
 * profile — otherwise enabling AI alongside test sources breaks wiring with
 * NoUniqueBeanDefinitionException.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "false", matchIfMissing = true)
public class FakeAiProvider implements AiProvider {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AiCompletionResult complete(AiCompletionRequest request) {
        return switch (request.feature()) {
            case "extract" -> new AiCompletionResult(
                    """
                    {"title": "Laptops", "priority": "HIGH",
                     "items": [{"description": "ThinkPad", "quantity": 20, "unitPriceMinor": 89900}]}""",
                    "fake-test", 12, 30);
            case "explain" -> new AiCompletionResult(
                    "Spend moves from requested to ordered to invoiced to paid; see cited queries.",
                    "fake-test", 20, 25);
            case "compare" -> new AiCompletionResult(
                    "Cheapest first with the spread noted; verify lead times before ordering.",
                    "fake-test", 15, 20);
            default -> new AiCompletionResult(
                    "Workspace answer grounded in the provided figures.", "fake-test", 10, 20);
        };
    }
}
