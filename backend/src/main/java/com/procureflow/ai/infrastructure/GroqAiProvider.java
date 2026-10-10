package com.procureflow.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.ai.application.AiCompletionRequest;
import com.procureflow.ai.application.AiCompletionResult;
import com.procureflow.ai.application.AiProvider;
import com.procureflow.shared.web.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Groq adapter behind the {@link AiProvider} port: the only place provider
 * SDKs or keys may appear. Created only when {@code AI_ENABLED=true}; without
 * a key it reports unavailable instead of failing the boot. Timeouts are
 * deliberately short so a slow provider degrades to 502, never a hung thread.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true", matchIfMissing = false)
public class GroqAiProvider implements AiProvider {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final ObjectMapper json;

    public GroqAiProvider(
            @Value("${app.ai.groq.api-key:}") String apiKey,
            @Value("${app.ai.groq.model:openai/gpt-oss-120b}") String model,
            ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.json = json;
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(requests).build();
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public AiCompletionResult complete(AiCompletionRequest request) {
        if (!isAvailable()) {
            throw ApiException.unavailable("AI_DISABLED", "AI features are disabled (AI_ENABLED=false)");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())),
                "max_tokens", request.maxTokens(),
                "temperature", request.temperature());
        JsonNode root;
        try {
            String raw = client.post()
                    .uri(ENDPOINT)
                    .headers(headers -> {
                        headers.setBearerAuth(apiKey);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            root = json.readTree(raw);
        } catch (RuntimeException e) {
            throw ApiException.badGateway("PROVIDER_ERROR", "The AI provider call failed");
        } catch (Exception e) {
            throw ApiException.badGateway("PROVIDER_MALFORMED", "The AI provider answer was unreadable");
        }
        String text = root.path("choices").path(0).path("message").path("content").asText(null);
        if (text == null || text.isBlank()) {
            throw ApiException.badGateway("PROVIDER_EMPTY", "The AI provider returned nothing usable");
        }
        JsonNode usage = root.path("usage");
        return new AiCompletionResult(
                text, model, usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0));
    }
}
