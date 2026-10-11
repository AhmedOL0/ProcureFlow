package com.procureflow.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.ai.application.AiCompletionRequest;
import com.procureflow.ai.application.AiCompletionResult;
import com.procureflow.shared.web.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/**
 * Provider transport against a loopback stub: one retry on 429/5xx, then an
 * honest code (429 PROVIDER_RATE_LIMITED, else 502). Client errors never
 * retry. No network beyond localhost, no Spring, no database.
 */
class GroqAiProviderTest {

    private static final String SUCCESS = """
            {"choices": [{"message": {"content": "grounded answer"}}],
             "usage": {"prompt_tokens": 5, "completion_tokens": 7}}""";

    private HttpServer server;
    private final Queue<Integer> script = new ArrayDeque<>();
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            Integer status = script.poll();
            byte[] body = status != null && status == 200
                    ? SUCCESS.getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status == null ? 500 : status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void retriesOnceOnRateLimitThenSucceeds() {
        script.add(429);
        script.add(200);

        AiCompletionResult result = provider().complete(request());

        assertEquals("grounded answer", result.text());
        assertEquals(5, result.promptTokens());
        assertEquals(7, result.completionTokens());
        assertEquals(2, hits.get());
    }

    @Test
    void persistentRateLimitMapsTo429() {
        script.add(429);
        script.add(429);

        ApiException failure = assertThrows(ApiException.class, () -> provider().complete(request()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.getStatus());
        assertEquals("PROVIDER_RATE_LIMITED", failure.getCode());
        assertEquals(2, hits.get());
    }

    @Test
    void persistentServerErrorMapsTo502() {
        script.add(500);
        script.add(500);

        ApiException failure = assertThrows(ApiException.class, () -> provider().complete(request()));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatus());
        assertEquals("PROVIDER_ERROR", failure.getCode());
        assertEquals(2, hits.get());
    }

    @Test
    void clientErrorNeverRetries() {
        script.add(400);

        ApiException failure = assertThrows(ApiException.class, () -> provider().complete(request()));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatus());
        assertEquals(1, hits.get());
    }

    private GroqAiProvider provider() {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
        return new GroqAiProvider("key", "model", new ObjectMapper(), RestClient.builder().build(), endpoint);
    }

    private static AiCompletionRequest request() {
        return new AiCompletionRequest("acme", "copilot", "system", "Where is spend accelerating?", 500, 0.2);
    }
}
