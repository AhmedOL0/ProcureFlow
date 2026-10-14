package com.procureflow.budget.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.identity.application.AuthAttemptThrottle;
import com.procureflow.shared.antivirus.ClamAvScanner;
import com.procureflow.shared.document.Csv;
import com.procureflow.shared.document.Table;
import com.procureflow.shared.document.Upload;
import com.procureflow.shared.document.Xlsx;
import com.procureflow.shared.web.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk budget-pot onboarding from .xlsx/.csv. Columns: {@code name}
 * (required), {@code period} (YYYY-MM, required), {@code amount} (major
 * units, up to 2 decimals, required), {@code currency} (3-letter, default
 * MAD). Headers are case-insensitive; unknown columns are ignored.
 *
 * <p>All-or-nothing like supplier import: any row failure rejects the file
 * with per-row errors and persists zero pots. Row numbers count data rows.
 */
@Service
public class BudgetImportService {

    public record RowError(int row, String field, String message) {
    }

    public record ImportResult(int created, List<RowError> errors) {
    }

    private final BudgetService budgets;
    private final AuthAttemptThrottle throttles;
    private final ClamAvScanner antivirus;
    private final ApplicationEventPublisher events;

    public BudgetImportService(
            BudgetService budgets,
            AuthAttemptThrottle throttles,
            ClamAvScanner antivirus,
            ApplicationEventPublisher events) {
        this.budgets = budgets;
        this.throttles = throttles;
        this.antivirus = antivirus;
        this.events = events;
    }

    @Transactional
    public ImportResult importFile(String tenantSlug, UUID actorId, String filename, byte[] content) {
        throttles.checkImport(actorId);
        Upload.FileKind kind = Upload.inspect(filename, content);
        antivirus.scan(content);
        Table table = kind == Upload.FileKind.XLSX ? Xlsx.read(content) : Csv.parse(content);
        if (!table.has("name") || !table.has("period") || !table.has("amount")) {
            throw ApiException.badRequest(
                    "MISSING_COLUMNS",
                    "Import needs 'name', 'period' and 'amount' columns",
                    Map.of("missing", "name, period, amount"));
        }
        List<Draft> drafts = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, String>> rows = table.rows();
        for (int i = 0; i < rows.size(); i++) {
            validateRow(i + 1, rows.get(i), tenantSlug, seen, drafts, errors);
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(
                    "IMPORT_VALIDATION_FAILED", "Import rejected: fix the rows and re-upload", details(errors));
        }
        for (Draft draft : drafts) {
            budgets.create(tenantSlug, actorId, draft.name, draft.period, draft.amountMinor, draft.currency);
        }
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug, actorId, "BUDGET_IMPORTED", "budget", null, Map.of(), Map.of("created", drafts.size())));
        return new ImportResult(drafts.size(), List.of());
    }

    private void validateRow(
            int row,
            Map<String, String> record,
            String tenantSlug,
            Set<String> seen,
            List<Draft> drafts,
            List<RowError> errors) {
        String name = value(record, "name");
        if (name.isEmpty()) {
            errors.add(new RowError(row, "name", "Name is required"));
            return;
        }
        if (name.length() > 200) {
            errors.add(new RowError(row, "name", "Name is longer than 200 characters"));
            return;
        }
        String period = value(record, "period");
        if (!BudgetService.isPeriod(period)) {
            errors.add(new RowError(row, "period", "Period must be YYYY-MM"));
            return;
        }
        if (!seen.add(name + "\0" + period)) {
            errors.add(new RowError(row, "name", "Duplicate pot inside this file: " + name + " / " + period));
            return;
        }
        if (budgets.existsByNameAndPeriod(tenantSlug, name, period)) {
            errors.add(new RowError(row, "name", "A pot with this name already exists for the period"));
            return;
        }
        Long amountMinor = amountOf(row, value(record, "amount"), errors);
        if (amountMinor == null) {
            return;
        }
        String currency = value(record, "currency");
        if (currency.isEmpty()) {
            currency = "MAD";
        } else {
            currency = currency.toUpperCase(java.util.Locale.ROOT);
            if (currency.length() != 3) {
                errors.add(new RowError(row, "currency", "Currency must be a 3-letter ISO code"));
                return;
            }
        }
        drafts.add(new Draft(name, period, amountMinor, currency));
    }

    private Long amountOf(int row, String raw, List<RowError> errors) {
        if (raw.isEmpty()) {
            errors.add(new RowError(row, "amount", "Amount is required"));
            return null;
        }
        try {
            long minor = new BigDecimal(raw.replace(",", "")).movePointRight(2).longValueExact();
            if (minor < 0) {
                errors.add(new RowError(row, "amount", "Amount cannot be negative"));
                return null;
            }
            return minor;
        } catch (ArithmeticException | NumberFormatException e) {
            errors.add(new RowError(row, "amount", "Amount must be a number with up to 2 decimals"));
            return null;
        }
    }

    private static String value(Map<String, String> record, String header) {
        String value = record.get(header);
        return value == null ? "" : value;
    }

    private static Map<String, String> details(List<RowError> errors) {
        Map<String, String> details = new LinkedHashMap<>();
        int shown = Math.min(errors.size(), Upload.MAX_ERROR_DETAILS);
        for (int i = 0; i < shown; i++) {
            RowError error = errors.get(i);
            details.put("row." + error.row() + "." + error.field(), error.message());
        }
        details.put("errorCount", String.valueOf(errors.size()));
        return details;
    }

    private record Draft(String name, String period, long amountMinor, String currency) {
    }
}
