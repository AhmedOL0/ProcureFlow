package com.procureflow.analytics.api;

import com.procureflow.analytics.application.AnalyticsService.ApprovalKpi;
import com.procureflow.analytics.application.AnalyticsService.SpendView;
import com.procureflow.analytics.application.AnalyticsService.SupplierKpi;
import com.procureflow.analytics.application.AnalyticsService;
import com.procureflow.analytics.application.SpendReportService;
import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.shared.document.Filenames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only KPIs over existing data. No transactional state is created
 * here; every number cites the underlying documents.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics", description = "Spend, supplier and approval KPIs")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final SpendReportService reports;

    public AnalyticsController(AnalyticsService analytics, SpendReportService reports) {
        this.analytics = analytics;
        this.reports = reports;
    }

    @GetMapping("/spend")
    @PreAuthorize("hasAuthority('analytics:read')")
    @Operation(summary = "Spend totals with category and monthly breakdowns")
    public ResponseEntity<SpendResponse> spend(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String period) {
        SpendView view = analytics.spend(principal.tenantId(), period);
        return ResponseEntity.ok(new SpendResponse(
                view.requestedMinor(),
                view.orderedMinor(),
                view.invoicedMinor(),
                view.paidMinor(),
                view.currencies(),
                view.byCurrency().stream()
                        .map(t -> new CurrencyTotalResponse(
                                t.currency(), t.requestedMinor(), t.orderedMinor(), t.invoicedMinor(), t.paidMinor()))
                        .toList(),
                view.byCategory().stream()
                        .map(c -> new CategoryResponse(c.category(), c.currency(), c.amountMinor()))
                        .toList(),
                view.byPeriod().stream()
                        .map(m -> new MonthResponse(
                                m.period(), m.currency(), m.orderedMinor(), m.invoicedMinor(), m.paidMinor()))
                        .toList()));
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('analytics:read')")
    @Operation(summary = "Supplier scorecard aggregation")
    public ResponseEntity<List<SupplierKpiResponse>> suppliers(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(analytics.suppliers(principal.tenantId()).stream()
                .map(AnalyticsController::supplier)
                .toList());
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('analytics:read')")
    @Operation(summary = "Approval queue depth and decision lead times")
    public ResponseEntity<ApprovalKpiResponse> approvals(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ApprovalKpi kpi = analytics.approvals(principal.tenantId());
        return ResponseEntity.ok(new ApprovalKpiResponse(
                kpi.pending(), kpi.decided(), kpi.avgLeadHours(), kpi.maxLeadHours()));
    }

    @GetMapping("/spend/report.pdf")
    @PreAuthorize("hasAuthority('analytics:read')")
    @Operation(summary = "Spend report over the page aggregates (PDF)")
    public ResponseEntity<byte[]> reportPdf(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String period) {
        byte[] content = reports.pdf(principal.tenantId(), principal.userId(), period);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.pdf("spend-report", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/spend/report.xlsx")
    @PreAuthorize("hasAuthority('analytics:read')")
    @Operation(summary = "Spend report over the page aggregates (xlsx, 4 sheets)")
    public ResponseEntity<byte[]> reportXlsx(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String period) {
        byte[] content = reports.xlsx(principal.tenantId(), principal.userId(), period);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.xlsx("spend-report", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    private static SupplierKpiResponse supplier(SupplierKpi kpi) {
        return new SupplierKpiResponse(
                kpi.supplierId(), kpi.supplierName(), kpi.periods(), kpi.avgOnTime(), kpi.avgQuality());
    }

    public record SpendResponse(
            long requestedMinor,
            long orderedMinor,
            long invoicedMinor,
            long paidMinor,
            List<String> currencies,
            List<CurrencyTotalResponse> byCurrency,
            List<CategoryResponse> byCategory,
            List<MonthResponse> byPeriod) {
    }

    public record CurrencyTotalResponse(
            String currency, long requestedMinor, long orderedMinor, long invoicedMinor, long paidMinor) {
    }

    public record CategoryResponse(String category, String currency, long amountMinor) {
    }

    public record MonthResponse(
            String period, String currency, long orderedMinor, long invoicedMinor, long paidMinor) {
    }

    public record SupplierKpiResponse(
            UUID supplierId, String supplierName, int periods, BigDecimal avgOnTime, BigDecimal avgQuality) {
    }

    public record ApprovalKpiResponse(long pending, long decided, Double avgLeadHours, Double maxLeadHours) {
    }
}
