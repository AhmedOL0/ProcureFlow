package com.procureflow.analytics.application;

import com.procureflow.analytics.application.AnalyticsService.ApprovalKpi;
import com.procureflow.analytics.application.AnalyticsService.MonthSlice;
import com.procureflow.analytics.application.AnalyticsService.SpendView;
import com.procureflow.analytics.application.AnalyticsService.SupplierKpi;
import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.shared.document.MoneyText;
import com.procureflow.shared.document.Pdf;
import com.procureflow.shared.document.Spreadsheet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spend reports over the same aggregates the Analytics page shows: KPI
 * totals, category and monthly breakdowns, supplier scorecards, approval
 * queue. Amounts render exactly as the page labels them (MAD); the PDF
 * meta says so explicitly rather than claiming a currency it cannot know.
 */
@Service
public class SpendReportService {

    // Display currency mirrors the Analytics page default (see money.ts).
    private static final String DISPLAY_CURRENCY = "MAD";

    private final AnalyticsService analytics;
    private final ApplicationEventPublisher events;

    public SpendReportService(AnalyticsService analytics, ApplicationEventPublisher events) {
        this.analytics = analytics;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public byte[] pdf(String tenantSlug, UUID actorId, String period) {
        SpendView spend = analytics.spend(tenantSlug, period);
        List<SupplierKpi> scorecards = analytics.suppliers(tenantSlug);
        ApprovalKpi approvals = analytics.approvals(tenantSlug);
        String scope = period == null || period.isBlank() ? "All time" : period;

        List<String> meta = new ArrayList<>();
        meta.add("Workspace: " + tenantSlug + "  |  Period: " + scope);
        meta.add("Amounts as shown on the Analytics page (" + DISPLAY_CURRENCY + ")");

        List<List<String>> totals = new ArrayList<>();
        totals.add(List.of("Requested", MoneyText.of(spend.requestedMinor(), DISPLAY_CURRENCY)));
        totals.add(List.of("Ordered", MoneyText.of(spend.orderedMinor(), DISPLAY_CURRENCY)));
        totals.add(List.of("Invoiced", MoneyText.of(spend.invoicedMinor(), DISPLAY_CURRENCY)));
        totals.add(List.of("Paid", MoneyText.of(spend.paidMinor(), DISPLAY_CURRENCY)));

        List<List<String>> categories = new ArrayList<>();
        for (var slice : spend.byCategory()) {
            categories.add(List.of(slice.category(), MoneyText.of(slice.amountMinor(), DISPLAY_CURRENCY)));
        }

        List<List<String>> months = new ArrayList<>();
        for (MonthSlice month : spend.byPeriod()) {
            months.add(List.of(
                    month.period(),
                    MoneyText.of(month.orderedMinor(), DISPLAY_CURRENCY),
                    MoneyText.of(month.invoicedMinor(), DISPLAY_CURRENCY),
                    MoneyText.of(month.paidMinor(), DISPLAY_CURRENCY)));
        }

        List<List<String>> vendors = new ArrayList<>();
        for (SupplierKpi kpi : scorecards) {
            vendors.add(List.of(
                    kpi.supplierName(),
                    String.valueOf(kpi.periods()),
                    percent(kpi.avgOnTime()),
                    percent(kpi.avgQuality())));
        }

        List<List<String>> queue = new ArrayList<>();
        queue.add(List.of("Pending decisions", String.valueOf(approvals.pending())));
        queue.add(List.of("Decided", String.valueOf(approvals.decided())));
        queue.add(List.of("Average lead", hours(approvals.avgLeadHours())));
        queue.add(List.of("Maximum lead", hours(approvals.maxLeadHours())));

        byte[] content = Pdf.document(
                "Spend report - " + scope,
                meta,
                List.of(
                        new Pdf.Section(null, new Pdf.Table(List.of(), List.of(30, 70), totals, Set.of(1))),
                        new Pdf.Section(
                                "By category",
                                new Pdf.Table(
                                        List.of("Category", "Amount"),
                                        List.of(60, 40),
                                        categories,
                                        Set.of(1))),
                        new Pdf.Section(
                                "By period",
                                new Pdf.Table(
                                        List.of("Period", "Ordered", "Invoiced", "Paid"),
                                        List.of(25, 25, 25, 25),
                                        months,
                                        Set.of(1, 2, 3))),
                        new Pdf.Section(
                                "Suppliers",
                                new Pdf.Table(
                                        List.of("Supplier", "Periods", "On-time", "Quality"),
                                        List.of(46, 14, 20, 20),
                                        vendors,
                                        Set.of(1, 2, 3))),
                        new Pdf.Section(
                                "Approvals",
                                new Pdf.Table(List.of(), List.of(30, 70), queue, Set.of()))),
                "ProcureFlow spend report",
                Instant.now());
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "spend_report",
                null,
                Map.of(),
                Map.of("format", "pdf", "period", scope)));
        return content;
    }

    @Transactional(readOnly = true)
    public byte[] xlsx(String tenantSlug, UUID actorId, String period) {
        SpendView spend = analytics.spend(tenantSlug, period);
        List<SupplierKpi> scorecards = analytics.suppliers(tenantSlug);
        String scope = period == null || period.isBlank() ? "All time" : period;

        List<List<Object>> totals = new ArrayList<>();
        totals.add(List.of("Requested", spend.requestedMinor() / 100.0));
        totals.add(List.of("Ordered", spend.orderedMinor() / 100.0));
        totals.add(List.of("Invoiced", spend.invoicedMinor() / 100.0));
        totals.add(List.of("Paid", spend.paidMinor() / 100.0));

        List<List<Object>> categories = new ArrayList<>();
        for (var slice : spend.byCategory()) {
            categories.add(List.of(slice.category(), slice.amountMinor() / 100.0));
        }

        List<List<Object>> months = new ArrayList<>();
        for (MonthSlice month : spend.byPeriod()) {
            months.add(List.of(
                    month.period(),
                    month.orderedMinor() / 100.0,
                    month.invoicedMinor() / 100.0,
                    month.paidMinor() / 100.0));
        }

        List<List<Object>> vendors = new ArrayList<>();
        for (SupplierKpi kpi : scorecards) {
            vendors.add(List.of(
                    kpi.supplierName(),
                    kpi.periods(),
                    rate(kpi.avgOnTime()),
                    rate(kpi.avgQuality())));
        }

        byte[] content = Spreadsheet.bookMulti(List.of(
                new Spreadsheet.SheetData("Totals", List.of("metric", "amount_" + DISPLAY_CURRENCY), totals),
                new Spreadsheet.SheetData(
                        "Categories", List.of("category", "amount_" + DISPLAY_CURRENCY), categories),
                new Spreadsheet.SheetData(
                        "Periods",
                        List.of("period", "ordered", "invoiced", "paid"),
                        months),
                new Spreadsheet.SheetData(
                        "Suppliers", List.of("supplier", "periods", "on_time", "quality"), vendors)));
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "spend_report",
                null,
                Map.of(),
                Map.of("format", "xlsx", "period", scope)));
        return content;
    }

    private static String percent(BigDecimal rate) {
        return rate == null ? "-" : rate.toPlainString() + "%";
    }

    private static Object rate(BigDecimal value) {
        return value == null ? "" : value.doubleValue();
    }

    private static String hours(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.US, "%.1fh", value);
    }
}
