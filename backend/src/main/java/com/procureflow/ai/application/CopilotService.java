package com.procureflow.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.ai.domain.AiUsageRecord;
import com.procureflow.ai.infrastructure.AiUsageRecordRepository;
import com.procureflow.analytics.application.AnalyticsService;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procurement copilot: KPI-grounded Q&A, spend explanations, natural-language
 * request extraction and quotation comparison. Every call validates input,
 * checks provider availability (503 when disabled), validates output and
 * meters usage per tenant in the same transaction. Prompts carry aggregates
 * only — no user identities, emails, or secrets leave the tenant scope.
 */
@Service
public class CopilotService {

    private static final Pattern PERIOD = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final AiProvider provider;
    private final AnalyticsService analytics;
    private final AiUsageRecordRepository usage;
    private final TenantProvisioning tenants;
    private final ObjectMapper json;

    public CopilotService(
            AiProvider provider,
            AnalyticsService analytics,
            AiUsageRecordRepository usage,
            TenantProvisioning tenants,
            ObjectMapper json) {
        this.provider = provider;
        this.analytics = analytics;
        this.usage = usage;
        this.tenants = tenants;
        this.json = json;
    }

    @Transactional
    public ChatAnswer chat(String tenantSlug, String question) {
        String asked = AiValidator.question(question);
        requireAvailable();
        AnalyticsService.SpendView spend = analytics.spend(tenantSlug, null);
        String figures = "requested=%d, ordered=%d, invoiced=%d, paid=%d (minor units)"
                .formatted(spend.requestedMinor(), spend.orderedMinor(), spend.invoicedMinor(), spend.paidMinor());
        AiCompletionResult result = provider.complete(new AiCompletionRequest(
                tenantSlug, "copilot", Prompts.CHAT_SYSTEM,
                "Workspace figures: " + figures + ". Question: " + asked, 500, 0.2));
        String answer = AiValidator.answer(result.text());
        meter(tenantSlug, "copilot", result);
        return new ChatAnswer(
                answer,
                List.of(
                        "requestedMinor=" + spend.requestedMinor() + " via GET /analytics/spend",
                        "orderedMinor=" + spend.orderedMinor() + " via GET /analytics/spend",
                        "invoicedMinor=" + spend.invoicedMinor() + " via GET /analytics/spend",
                        "paidMinor=" + spend.paidMinor() + " via GET /analytics/spend"),
                result.model());
    }

    @Transactional
    public ChatAnswer explain(String tenantSlug, String period) {
        if (period != null) {
            requirePeriod(period);
        }
        requireAvailable();
        AnalyticsService.SpendView spend = analytics.spend(tenantSlug, period);
        AnalyticsService.ApprovalKpi approvals = analytics.approvals(tenantSlug);
        String top = spend.byCategory().isEmpty()
                ? "none"
                : spend.byCategory().get(0).category() + "=" + spend.byCategory().get(0).amountMinor();
        AiCompletionResult result = provider.complete(new AiCompletionRequest(
                tenantSlug, "explain", Prompts.EXPLAIN_SYSTEM,
                "Period %s: requested=%d, ordered=%d, invoiced=%d, paid=%d, top category %s, approvals pending=%d decided=%d."
                        .formatted(
                                period == null ? "all time" : period,
                                spend.requestedMinor(), spend.orderedMinor(), spend.invoicedMinor(),
                                spend.paidMinor(), top, approvals.pending(), approvals.decided()),
                600, 0.2));
        String answer = AiValidator.answer(result.text());
        meter(tenantSlug, "explain", result);
        return new ChatAnswer(answer, List.of("GET /analytics/spend" + (period == null ? "" : "?period=" + period),
                "GET /analytics/approvals"), result.model());
    }

    @Transactional
    public AiValidator.DraftRequest extract(String tenantSlug, String text) {
        String sentence = AiValidator.question(text);
        requireAvailable();
        AiCompletionResult result = provider.complete(new AiCompletionRequest(
                tenantSlug, "extract", Prompts.EXTRACT_SYSTEM, sentence, 400, 0.0));
        AiValidator.DraftRequest draft = AiValidator.draft(result.text(), json);
        meter(tenantSlug, "extract", result);
        return draft;
    }

    @Transactional
    public ChatAnswer compare(String tenantSlug, List<QuoteInput> quotes) {
        if (quotes == null || quotes.size() < 2 || quotes.size() > 10) {
            throw ApiException.badRequest("QUOTES_REQUIRED", "Provide between 2 and 10 quotations");
        }
        for (QuoteInput quote : quotes) {
            if (quote.supplier() == null || quote.supplier().isBlank() || quote.amountMinor() < 0) {
                throw ApiException.badRequest("INVALID_QUOTE", "Each quote needs a supplier and a non-negative total");
            }
        }
        requireAvailable();
        StringBuilder table = new StringBuilder();
        for (QuoteInput quote : quotes) {
            table.append(quote.supplier().trim()).append(": ").append(quote.amountMinor()).append("; ");
        }
        AiCompletionResult result = provider.complete(new AiCompletionRequest(
                tenantSlug, "compare", Prompts.COMPARE_SYSTEM, "Quotations (minor units) — " + table, 400, 0.2));
        String answer = AiValidator.answer(result.text());
        meter(tenantSlug, "compare", result);
        return new ChatAnswer(answer, List.of(quotes.size() + " quotations compared"), result.model());
    }

    @Transactional(readOnly = true)
    public UsageView usage(String tenantSlug, String period) {
        if (period != null) {
            requirePeriod(period);
        }
        List<AiUsageRecord> rows = usage.findAllByTenantIdOrderByCreatedAtDesc(
                tenants.requireTenantId(tenantSlug));
        Map<String, long[]> byFeature = new LinkedHashMap<>();
        long calls = 0;
        long promptTokens = 0;
        long completionTokens = 0;
        for (AiUsageRecord row : rows) {
            if (period != null && !period.equals(monthOf(row))) {
                continue;
            }
            calls++;
            promptTokens += row.getPromptTokens();
            completionTokens += row.getCompletionTokens();
            byFeature.computeIfAbsent(row.getFeature(), f -> new long[3])[0]++;
            byFeature.computeIfAbsent(row.getFeature(), f -> new long[3])[1] += row.getPromptTokens();
            byFeature.computeIfAbsent(row.getFeature(), f -> new long[3])[2] += row.getCompletionTokens();
        }
        List<FeatureUsage> features = byFeature.entrySet().stream()
                .map(e -> new FeatureUsage(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
        return new UsageView(calls, promptTokens, completionTokens, features);
    }

    private void requireAvailable() {
        if (!provider.isAvailable()) {
            throw ApiException.unavailable("AI_DISABLED", "AI features are disabled (AI_ENABLED=false)");
        }
    }

    private void meter(String tenantSlug, String feature, AiCompletionResult result) {
        usage.save(new AiUsageRecord(
                tenants.requireTenantId(tenantSlug),
                feature,
                result.model(),
                Prompts.VERSION,
                result.promptTokens(),
                result.completionTokens()));
    }

    private static String monthOf(AiUsageRecord row) {
        return YearMonth.from(LocalDate.ofInstant(row.getCreatedAt(), ZoneOffset.UTC)).toString();
    }

    private static void requirePeriod(String period) {
        if (!PERIOD.matcher(period).matches()) {
            throw ApiException.badRequest("INVALID_PERIOD", "Period must be YYYY-MM");
        }
    }

    public record ChatAnswer(String answer, List<String> citations, String model) {
    }

    public record QuoteInput(String supplier, long amountMinor) {
    }

    public record UsageView(long calls, long promptTokens, long completionTokens, List<FeatureUsage> byFeature) {
    }

    public record FeatureUsage(String feature, long calls, long promptTokens, long completionTokens) {
    }
}
