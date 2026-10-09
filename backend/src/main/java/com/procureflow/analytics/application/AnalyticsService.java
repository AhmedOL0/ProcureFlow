package com.procureflow.analytics.application;

import com.procureflow.invoice.application.InvoiceStatsPort;
import com.procureflow.procurement.application.RequestStatsPort;
import com.procureflow.purchaseorder.application.OrderStatsPort;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.application.SupplierStatsPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only KPIs over existing data. This module owns no transactional
 * state: it aggregates the read ports of procurement, orders, invoices and
 * suppliers in memory (precomputed views only if measured slow). Money sums
 * minor units as-is; workspaces are single-currency (MAD default) today.
 */
@Service
public class AnalyticsService {

    private static final Pattern PERIOD = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final RequestStatsPort requests;
    private final OrderStatsPort orders;
    private final InvoiceStatsPort invoices;
    private final SupplierStatsPort suppliers;

    public AnalyticsService(
            RequestStatsPort requests,
            OrderStatsPort orders,
            InvoiceStatsPort invoices,
            SupplierStatsPort suppliers) {
        this.requests = requests;
        this.orders = orders;
        this.invoices = invoices;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public SpendView spend(String tenantSlug, String period) {
        if (period != null) {
            requirePeriod(period);
        }
        long requested = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (RequestStatsPort.RequestStat request : requests.requestStats(tenantSlug)) {
            if ("CANCELLED".equals(request.status()) || !inPeriod(request.createdAt(), period)) {
                continue;
            }
            for (RequestStatsPort.ItemStat item : request.items()) {
                long line = (long) item.quantity() * item.unitPriceMinor();
                requested += line;
                String category = item.category() == null || item.category().isBlank()
                        ? "Uncategorized"
                        : item.category();
                byCategory.merge(category, line, Long::sum);
            }
        }
        long ordered = 0;
        Map<String, long[]> monthly = new LinkedHashMap<>();
        for (OrderStatsPort.OrderStat order : orders.orderStats(tenantSlug)) {
            if ("CANCELLED".equals(order.status()) || !inPeriod(order.createdAt(), period)) {
                continue;
            }
            long total = order.lines().stream()
                    .mapToLong(l -> (long) l.quantity() * l.unitPriceMinor())
                    .sum();
            ordered += total;
            monthly.computeIfAbsent(monthOf(order.createdAt()), m -> new long[3])[0] += total;
        }
        long invoiced = 0;
        long paid = 0;
        for (InvoiceStatsPort.InvoiceStat invoice : invoices.invoiceStats(tenantSlug)) {
            if (!inPeriod(invoice.createdAt(), period)) {
                continue;
            }
            invoiced += invoice.totalMinor();
            paid += invoice.paidMinor();
            monthly.computeIfAbsent(monthOf(invoice.createdAt()), m -> new long[3])[1] += invoice.totalMinor();
            monthly.computeIfAbsent(monthOf(invoice.createdAt()), m -> new long[3])[2] += invoice.paidMinor();
        }
        List<CategorySlice> categories = byCategory.entrySet().stream()
                .map(e -> new CategorySlice(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.amountMinor(), a.amountMinor()))
                .toList();
        List<MonthSlice> months = monthly.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new MonthSlice(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
        return new SpendView(requested, ordered, invoiced, paid, categories, months);
    }

    @Transactional(readOnly = true)
    public List<SupplierKpi> suppliers(String tenantSlug) {
        Map<UUID, List<SupplierStatsPort.ScoreStat>> grouped = suppliers.scores(tenantSlug).stream()
                .collect(Collectors.groupingBy(
                        SupplierStatsPort.ScoreStat::supplierId, LinkedHashMap::new, Collectors.toList()));
        List<SupplierKpi> result = new ArrayList<>();
        for (List<SupplierStatsPort.ScoreStat> rows : grouped.values()) {
            result.add(new SupplierKpi(
                    rows.get(0).supplierId(),
                    rows.get(0).supplierName(),
                    rows.size(),
                    average(rows.stream().map(SupplierStatsPort.ScoreStat::onTimeRate).toList()),
                    average(rows.stream().map(SupplierStatsPort.ScoreStat::qualityScore).toList())));
        }
        result.sort((a, b) -> a.supplierName().compareToIgnoreCase(b.supplierName()));
        return result;
    }

    @Transactional(readOnly = true)
    public ApprovalKpi approvals(String tenantSlug) {
        long pending = 0;
        long decided = 0;
        List<Double> leadHours = new ArrayList<>();
        for (RequestStatsPort.RequestStat request : requests.requestStats(tenantSlug)) {
            if ("SUBMITTED".equals(request.status())) {
                pending++;
            }
            // ORDERED implies approved: the request passed a decision on its way out.
            if (("APPROVED".equals(request.status()) || "REJECTED".equals(request.status())
                            || "ORDERED".equals(request.status()))
                    && request.submittedAt() != null
                    && request.decidedAt() != null) {
                decided++;
                leadHours.add((request.decidedAt().toEpochMilli() - request.submittedAt().toEpochMilli()) / 3600000.0);
            }
        }
        Double avg = leadHours.isEmpty()
                ? null
                : Math.round(leadHours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100.0) / 100.0;
        Double max = leadHours.isEmpty()
                ? null
                : Math.round(leadHours.stream().mapToDouble(Double::doubleValue).max().orElse(0.0) * 100.0) / 100.0;
        return new ApprovalKpi(pending, decided, avg, max);
    }

    private static boolean inPeriod(Instant at, String period) {
        return period == null || (at != null && period.equals(monthOf(at)));
    }

    private static String monthOf(Instant at) {
        return YearMonth.from(LocalDate.ofInstant(at, ZoneOffset.UTC)).toString();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(v -> v != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private static void requirePeriod(String period) {
        if (!PERIOD.matcher(period).matches()) {
            throw ApiException.badRequest("INVALID_PERIOD", "Period must be YYYY-MM");
        }
    }

    public record SpendView(
            long requestedMinor,
            long orderedMinor,
            long invoicedMinor,
            long paidMinor,
            List<CategorySlice> byCategory,
            List<MonthSlice> byPeriod) {
    }

    public record CategorySlice(String category, long amountMinor) {
    }

    public record MonthSlice(String period, long orderedMinor, long invoicedMinor, long paidMinor) {
    }

    public record SupplierKpi(UUID supplierId, String supplierName, int periods, BigDecimal avgOnTime,
            BigDecimal avgQuality) {
    }

    public record ApprovalKpi(long pending, long decided, Double avgLeadHours, Double maxLeadHours) {
    }
}
