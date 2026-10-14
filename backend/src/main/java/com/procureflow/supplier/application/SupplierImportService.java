package com.procureflow.supplier.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.identity.application.AuthAttemptThrottle;
import com.procureflow.shared.antivirus.ClamAvScanner;
import com.procureflow.shared.document.Csv;
import com.procureflow.shared.document.Table;
import com.procureflow.shared.document.Upload;
import com.procureflow.shared.document.Xlsx;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.domain.Supplier;
import com.procureflow.supplier.domain.SupplierCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk supplier onboarding from .xlsx/.csv. Columns: {@code name} (required),
 * {@code email}, {@code phone}, {@code tax_id}, {@code address},
 * {@code status} (active/inactive/suspended, default active),
 * {@code categories} (semicolon-separated catalog names). Headers are
 * case-insensitive; unknown columns are ignored.
 *
 * <p>All-or-nothing: every row validates first, and any failure rejects the
 * whole file with per-row errors — re-uploading a corrected file can never
 * double-create. Row numbers count data rows (the header is not row 1).
 */
@Service
public class SupplierImportService {

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SupplierService suppliers;
    private final CategoryService categories;
    private final AuthAttemptThrottle throttles;
    private final ClamAvScanner antivirus;
    private final ApplicationEventPublisher events;

    public SupplierImportService(
            SupplierService suppliers,
            CategoryService categories,
            AuthAttemptThrottle throttles,
            ClamAvScanner antivirus,
            ApplicationEventPublisher events) {
        this.suppliers = suppliers;
        this.categories = categories;
        this.throttles = throttles;
        this.antivirus = antivirus;
        this.events = events;
    }

    public record RowError(int row, String field, String message) {
    }

    public record ImportResult(int created, List<RowError> errors) {
    }

    @Transactional
    public ImportResult importFile(String tenantSlug, UUID actorId, String filename, byte[] content) {
        throttles.checkImport(actorId);
        Upload.FileKind kind = Upload.inspect(filename, content);
        antivirus.scan(content);
        Table table = kind == Upload.FileKind.XLSX ? Xlsx.read(content) : Csv.parse(content);
        if (!table.has("name")) {
            throw ApiException.badRequest(
                    "MISSING_COLUMNS", "Import needs at least a 'name' column", Map.of("missing", "name"));
        }
        Map<String, UUID> catalog = catalogByName(tenantSlug);
        List<Draft> drafts = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, String>> rows = table.rows();
        for (int i = 0; i < rows.size(); i++) {
            validateRow(i + 1, rows.get(i), catalog, seen, drafts, errors, tenantSlug);
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("IMPORT_VALIDATION_FAILED", "Import rejected: fix the rows and re-upload", details(errors));
        }
        for (Draft draft : drafts) {
            Supplier created = suppliers.create(
                    tenantSlug, draft.name, draft.taxId, draft.email, draft.phone, draft.address);
            created.setStatus(draft.status);
            if (!draft.categoryIds.isEmpty()) {
                suppliers.assignCategories(tenantSlug, created.getId(), draft.categoryIds);
            }
        }
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug, actorId, "SUPPLIER_IMPORTED", "supplier", null, Map.of(), Map.of("created", drafts.size())));
        return new ImportResult(drafts.size(), List.of());
    }

    private Map<String, UUID> catalogByName(String tenantSlug) {
        Map<String, UUID> catalog = new HashMap<>();
        for (SupplierCategory category : categories.list(tenantSlug)) {
            catalog.putIfAbsent(category.getName().toLowerCase(java.util.Locale.ROOT), category.getId());
        }
        return catalog;
    }

    private void validateRow(
            int row,
            Map<String, String> record,
            Map<String, UUID> catalog,
            Set<String> seen,
            List<Draft> drafts,
            List<RowError> errors,
            String tenantSlug) {
        String name = value(record, "name");
        if (name.isEmpty()) {
            errors.add(new RowError(row, "name", "Name is required"));
            return;
        }
        if (name.length() > 200) {
            errors.add(new RowError(row, "name", "Name is longer than 200 characters"));
            return;
        }
        if (!seen.add(name)) {
            errors.add(new RowError(row, "name", "Duplicate name inside this file: " + name));
            return;
        }
        if (suppliers.existsByName(tenantSlug, name)) {
            errors.add(new RowError(row, "name", "A supplier with this name already exists"));
            return;
        }
        String email = value(record, "email");
        if (!email.isEmpty() && (!EMAIL.matcher(email).matches() || email.length() > 320)) {
            errors.add(new RowError(row, "email", "Not a valid email address"));
            return;
        }
        String phone = value(record, "phone");
        if (phone.length() > 50) {
            errors.add(new RowError(row, "phone", "Phone is longer than 50 characters"));
            return;
        }
        String taxId = value(record, "tax_id");
        if (taxId.isEmpty()) {
            taxId = value(record, "taxid");
        }
        if (taxId.length() > 100) {
            errors.add(new RowError(row, "tax_id", "Tax ID is longer than 100 characters"));
            return;
        }
        String address = value(record, "address");
        if (address.length() > 500) {
            errors.add(new RowError(row, "address", "Address is longer than 500 characters"));
            return;
        }
        Supplier.Status status = statusOf(row, value(record, "status"), errors);
        if (status == null) {
            return;
        }
        Set<UUID> categoryIds = categoriesOf(row, value(record, "categories"), catalog, errors);
        if (categoryIds == null) {
            return;
        }
        drafts.add(new Draft(
                name,
                taxId.isEmpty() ? null : taxId,
                email.isEmpty() ? null : email,
                phone.isEmpty() ? null : phone,
                address.isEmpty() ? null : address,
                status,
                categoryIds));
    }

    private Supplier.Status statusOf(int row, String raw, List<RowError> errors) {
        if (raw.isEmpty()) {
            return Supplier.Status.ACTIVE;
        }
        try {
            return Supplier.Status.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(new RowError(row, "status", "Unknown status '" + raw + "': active, inactive or suspended"));
            return null;
        }
    }

    private Set<UUID> categoriesOf(
            int row, String raw, Map<String, UUID> catalog, List<RowError> errors) {
        Set<UUID> ids = new HashSet<>();
        if (raw.isEmpty()) {
            return ids;
        }
        for (String name : raw.split(";")) {
            String candidate = name.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            UUID id = catalog.get(candidate.toLowerCase(java.util.Locale.ROOT));
            if (id == null) {
                errors.add(new RowError(row, "categories", "Unknown category: " + candidate));
                return null;
            }
            ids.add(id);
        }
        return ids;
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

    private record Draft(
            String name,
            String taxId,
            String email,
            String phone,
            String address,
            Supplier.Status status,
            Set<UUID> categoryIds) {
    }
}
