package com.procureflow.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.shared.web.ApiException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider-output validation. Anything the model returns is untrusted until
 * it passes here: prose answers stay bounded, extraction JSON must match the
 * draft shape exactly.
 */
public final class AiValidator {

    static final int MAX_ANSWER = 4000;
    static final int MAX_QUESTION = 2000;

    private AiValidator() {
        // static only
    }

    public static String answer(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.badGateway("PROVIDER_EMPTY", "The AI provider returned nothing usable");
        }
        if (text.length() > MAX_ANSWER) {
            throw ApiException.badGateway("PROVIDER_TOO_LONG", "The AI provider answer exceeded limits");
        }
        return text.trim();
    }

    public static String question(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("QUESTION_REQUIRED", "A question or sentence is required");
        }
        if (text.length() > MAX_QUESTION) {
            throw ApiException.badRequest("QUESTION_TOO_LONG", "Keep input under 2000 characters");
        }
        return text.trim();
    }

    public static DraftRequest draft(String json, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw ApiException.badGateway("PROVIDER_MALFORMED", "The AI provider returned invalid JSON");
        }
        if (!root.isObject()) {
            throw ApiException.badGateway("PROVIDER_MALFORMED", "The AI provider returned invalid JSON");
        }
        String title = text(root, "title");
        if (title == null || title.isBlank() || title.length() > 200) {
            throw ApiException.badGateway("PROVIDER_MALFORMED", "Extracted draft has no usable title");
        }
        JsonNode items = root.get("items");
        if (items == null || !items.isArray() || items.isEmpty() || items.size() > 20) {
            throw ApiException.badGateway("PROVIDER_MALFORMED", "Extracted draft has no usable items");
        }
        List<DraftItem> parsed = new ArrayList<>();
        for (JsonNode item : items) {
            String description = text(item, "description");
            int quantity = item.has("quantity") ? item.get("quantity").asInt(-1) : -1;
            long price = item.has("unitPriceMinor") ? item.get("unitPriceMinor").asLong(-1) : -1;
            if (description == null
                    || description.isBlank()
                    || description.length() > 500
                    || quantity < 1
                    || price < 0) {
                throw ApiException.badGateway("PROVIDER_MALFORMED", "Extracted draft has an invalid item");
            }
            parsed.add(new DraftItem(description.trim(), quantity, price));
        }
        String priority = text(root, "priority");
        if (priority != null && !priority.matches("LOW|MEDIUM|HIGH|URGENT")) {
            priority = null;
        }
        return new DraftRequest(title.trim(), priority, List.copyOf(parsed));
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && node.get(field).isTextual() ? node.get(field).asText() : null;
    }

    public record DraftRequest(String title, String priority, List<DraftItem> items) {
    }

    public record DraftItem(String description, int quantity, long unitPriceMinor) {
    }
}
