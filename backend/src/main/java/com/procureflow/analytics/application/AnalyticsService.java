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
 * state. Month-filtered views read only their month; the all-time path uses
 * DB-side sums and stamp projections so no item or line collections load no
 * matter how large tenant history grows. Money sums
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
            return spendForMonth(tenantSlug, period);
        }
        return spendAllTime(tenantSlug);
    }

    /**
     * Bounded all-time path: DB-side sums and stamp projections, no request
     * items or order lines load no matter how large tenant history grows.
     * Query-count gated in AnalyticsIT.
     */
    private SpendView spendAllTime(String tenantSlug) {
        Accumulator acc = new Accumulator();
        for (RequestStatsPort.CategoryTotal slice : requests.requestTotals(tenantSlug).byCategory()) {
            acc.requested(slice.currency(), slice.amountMinor());
            acc.category(slice.category(), slice.currency(), slice.amountMinor());
        }
        for (OrderStatsPort.OrderTotal order : orders.orderTotals(tenantSlug)) {
            if ("CANCELLED".equals(order.status())) {
                continue;
            }
            acc.ordered(order.currency(), order.totalMinor(), monthOf(order.createdAt()));
        }
        for (InvoiceStatsPort.InvoiceTotal invoice : invoices.invoiceTotals(tenantSlug)) {
            acc.invoiced(invoice.currency(), invoice.totalMinor(), monthOf(invoice.createdAt()));
            acc.paid(invoice.currency(), invoice.paidMinor(), monthOf(invoice.createdAt()));
        }
        return acc.view();
    }

    private SpendView spendForMonth(String tenantSlug, String period) {
        // A filtered view reads only its month: tenant history stays out of
        // the persistence context no matter how large it grows.
        YearMonth month = YearMonth.parse(period);
        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Accumulator acc = new Accumulator();
        for (RequestStatsPort.RequestStat request :
                requests.requestStats(tenantSlug, from, to)) {
            if ("CANCELLED".equals(request.status()) || !inPeriod(request.createdAt(), period)) {
                continue;
            }
            for (RequestStatsPort.ItemStat item : request.items()) {
                long line = (long) item.quantity() * item.unitPriceMinor();
                acc.requested(item.currency(), line);
                String category = item.category() == null || item.category().isBlank()
                        ? "Uncategorized"
                        : item.category();
                acc.category(category, item.currency(), line);
            }
        }
        for (OrderStatsPort.OrderStat order : orders.orderStats(tenantSlug, from, to)) {
            if ("CANCELLED".equals(order.status()) || !inPeriod(order.createdAt(), period)) {
                continue;
            }
            long total = order.lines().stream()
                    .mapToLong(l -> (long) l.quantity() * l.unitPriceMinor())
                    .sum();
            acc.ordered(order.currency(), total, monthOf(order.createdAt()));
        }
        for (InvoiceStatsPort.InvoiceStat invoice : invoices.invoiceStats(tenantSlug, from, to)) {
            if (!inPeriod(invoice.createdAt(), period)) {
                continue;
            }
            acc.invoiced(invoice.currency(), invoice.totalMinor(), monthOf(invoice.createdAt()));
            acc.paid(invoice.currency(), invoice.paidMinor(), monthOf(invoice.createdAt()));
        }
        return acc.view();
    }

    /**
     * Per-currency accumulator behind both spend paths. Legacy merged totals
     * stay on the view for backward compatibility but are meaningful only
     * when a single currency flows; the UI branches on {@code currencies}.
     */
    private static final class Accumulator {
        private final Map<String, long[]> kpi = new LinkedHashMap<>();
        private final Map<String, Map<String, Long>> categories = new LinkedHashMap<>();
        private final Map<String, long[]> monthly = new LinkedHashMap<>();

        void requested(String currency, long amount) {
            kpi.computeIfAbsent(code(currency), c -> new long[4])[0] += amount;
        }

        void ordered(String currency, long amount, String period) {
            kpi.computeIfAbsent(code(currency), c -> new long[4])[1] += amount;
            monthly.computeIfAbsent(key(period, currency), k -> new long[3])[0] += amount;
        }

        void invoiced(String currency, long amount, String period) {
            kpi.computeIfAbsent(code(currency), c -> new long[4])[2] += amount;
            monthly.computeIfAbsent(key(period, currency), k -> new long[3])[1] += amount;
        }

        void paid(String currency, long amount, String period) {
            kpi.computeIfAbsent(code(currency), c -> new long[4])[3] += amount;
            monthly.computeIfAbsent(key(period, currency), k -> new long[3])[2] += amount;
        }

        void category(String category, String currency, long amount) {
            categories
                    .computeIfAbsent(category, c -> new LinkedHashMap<>())
                    .merge(code(currency), amount, Long::sum);
        }

        SpendView view() {
            List<String> codes = kpi.keySet().stream().sorted().toList();
            List<CurrencyTotal> byCurrency = new ArrayList<>();
            long requested = 0;
            long ordered = 0;
            long invoiced = 0;
            long paid = 0;
            for (String code : codes) {
                long[] sums = kpi.get(code);
                byCurrency.add(new CurrencyTotal(code, sums[0], sums[1], sums[2], sums[3]));
                requested += sums[0];
                ordered += sums[1];
                invoiced += sums[2];
                paid += sums[3];
            }
            List<CategorySlice> slices = new ArrayList<>();
            for (Map.Entry<String, Map<String, Long>> entry : categories.entrySet()) {
                for (Map.Entry<String, Long> perCode : entry.getValue().entrySet()) {
                    slices.add(new CategorySlice(entry.getKey(), perCode.getKey(), perCode.getValue()));
                }
            }
            slices.sort((a, b) -> Long.compare(b.amountMinor(), a.amountMinor()));
            List<MonthSlice> months = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : monthly.entrySet()) {
                String[] parts = entry.getKey().split("\0", -1);
                months.add(new MonthSlice(
                        parts[0], parts[1], entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]));
            }
            months.sort((a, b) -> (a.period() + "\0" + a.currency()).compareTo(b.period() + "\0" + b.currency()));
            return new SpendView(requested, ordered, invoiced, paid, codes, byCurrency, slices, months);
        }

        private static String key(String period, String currency) {
            return period + "\0" + code(currency);
        }

        // Legacy rows predate currency tracking in a MAD-only product;
        // anything blank buckets as MAD rather than vanishing.
        private static String code(String currency) {
            return currency == null || currency.isBlank() ? "MAD" : currency;
        }
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
        for (RequestStatsPort.LeadRow request : requests.requestLeads(tenantSlug)) {
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
            List<String> currencies,
            List<CurrencyTotal> byCurrency,
            List<CategorySlice> byCategory,
            List<MonthSlice> byPeriod) {
    }

    public record CurrencyTotal(
            String currency, long requestedMinor, long orderedMinor, long invoicedMinor, long paidMinor) {
    }

    public record CategorySlice(String category, String currency, long amountMinor) {
    }

    public record MonthSlice(
            String period, String currency, long orderedMinor, long invoicedMinor, long paidMinor) {
    }

    public record SupplierKpi(UUID supplierId, String supplierName, int periods, BigDecimal avgOnTime,
            BigDecimal avgQuality) {
    }

    public record ApprovalKpi(long pending, long decided, Double avgLeadHours, Double maxLeadHours) {
    }
}
