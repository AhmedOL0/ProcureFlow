package com.procureflow.ai.api;

import com.procureflow.ai.application.AiValidator;
import com.procureflow.ai.application.CopilotService;
import com.procureflow.identity.application.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Procurement copilot. Answers are grounded in workspace KPIs with citations;
 * every call is metered per tenant. Disabled tenants answer 503.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "ai-copilot", description = "KPI-grounded procurement copilot")
public class AiController {

    private final CopilotService copilot;

    public AiController(CopilotService copilot) {
        this.copilot = copilot;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Ask about workspace spend (answers cite KPI queries)")
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ChatRequest request) {
        CopilotService.ChatAnswer answer = copilot.chat(principal.tenantId(), request.question());
        return ResponseEntity.ok(new ChatResponse(answer.answer(), answer.citations(), answer.model()));
    }

    @PostMapping("/explain-spend")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Explain spend figures for a period (or all time)")
    public ResponseEntity<ChatResponse> explain(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody(required = false) ExplainRequest request) {
        CopilotService.ChatAnswer answer =
                copilot.explain(principal.tenantId(), request == null ? null : request.period());
        return ResponseEntity.ok(new ChatResponse(answer.answer(), answer.citations(), answer.model()));
    }

    @PostMapping("/extract-request")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Turn a sentence into a structured request draft")
    public ResponseEntity<DraftResponse> extract(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ChatRequest request) {
        AiValidator.DraftRequest draft = copilot.extract(principal.tenantId(), request.question());
        return ResponseEntity.ok(new DraftResponse(
                draft.title(),
                draft.priority(),
                draft.items().stream()
                        .map(i -> new DraftItemResponse(i.description(), i.quantity(), i.unitPriceMinor()))
                        .toList()));
    }

    @PostMapping("/compare-quotations")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Compare 2-10 supplier quotations")
    public ResponseEntity<ChatResponse> compare(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CompareRequest request) {
        CopilotService.ChatAnswer answer = copilot.compare(
                principal.tenantId(),
                request.quotes().stream()
                        .map(q -> new CopilotService.QuoteInput(q.supplier(), q.amountMinor()))
                        .toList());
        return ResponseEntity.ok(new ChatResponse(answer.answer(), answer.citations(), answer.model()));
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Metered AI calls for the workspace, optionally per month")
    public ResponseEntity<UsageResponse> usage(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String period) {
        CopilotService.UsageView view = copilot.usage(principal.tenantId(), period);
        return ResponseEntity.ok(new UsageResponse(
                view.calls(),
                view.promptTokens(),
                view.completionTokens(),
                view.byFeature().stream()
                        .map(f -> new FeatureUsageResponse(f.feature(), f.calls(), f.promptTokens(),
                                f.completionTokens()))
                        .toList()));
    }

    public record ChatRequest(@NotNull String question) {
    }

    public record ExplainRequest(String period) {
    }

    public record ChatResponse(String answer, List<String> citations, String model) {
    }

    public record DraftResponse(String title, String priority, List<DraftItemResponse> items) {
    }

    public record DraftItemResponse(String description, int quantity, long unitPriceMinor) {
    }

    public record CompareRequest(@NotEmpty List<@Valid QuoteInput> quotes) {
    }

    public record QuoteInput(@NotNull String supplier, long amountMinor) {
    }

    public record UsageResponse(long calls, long promptTokens, long completionTokens,
            List<FeatureUsageResponse> byFeature) {
    }

    public record FeatureUsageResponse(String feature, long calls, long promptTokens, long completionTokens) {
    }
}
