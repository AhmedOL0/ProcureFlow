package com.procureflow.shared.document;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Attachment filenames for generated documents: ASCII-safe, tenant-scoped,
 * dated. Slugs are already slug-safe; dates are UTC so repeated exports
 * sort and never collide within a day.
 */
public final class Filenames {

    private Filenames() {
    }

    public static String xlsx(String base, String tenantSlug) {
        return stamped(base, tenantSlug, "xlsx");
    }

    public static String pdf(String base, String tenantSlug) {
        return stamped(base, tenantSlug, "pdf");
    }

    static String stamped(String base, String tenantSlug, String extension) {
        String safeBase = slug(base);
        String safeTenant = slug(tenantSlug);
        return "%s-%s-%s.%s".formatted(safeBase, safeTenant, LocalDate.now(ZoneOffset.UTC), extension);
    }

    private static String slug(String value) {
        String clean = value == null ? "export" : value.trim().toLowerCase(Locale.ROOT);
        clean = clean.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return clean.isEmpty() ? "export" : clean;
    }
}
