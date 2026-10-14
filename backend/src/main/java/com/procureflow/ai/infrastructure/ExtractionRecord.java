package com.procureflow.ai.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A validated AI extraction frozen at answer time: the tenant's own input
 * sentence plus the draft the model produced for it, with provenance.
 * Derived history only — requests, orders and invoices stay authoritative
 * in PostgreSQL, and draft prices remain proposals until the guarded
 * create path re-validates them. Retention is a 90-day TTL (see
 * ExtractionIndexes); schemaVersion lets future shapes coexist.
 */
@Document(collection = "ai_extractions")
public class ExtractionRecord {

    private static final int SCHEMA_VERSION = 1;

    @Id
    private String id;

    private String tenantSlug;

    private int schemaVersion;

    private String modelIdentifier;

    private String promptVersion;

    private String input;

    private String title;

    private String priority;

    private List<DraftItem> items = new ArrayList<>();

    private String createdRequestId;

    private Instant createdAt;

    private ExtractionRecord() {
        // mapping only
    }

    public static ExtractionRecord create(
            String tenantSlug,
            String modelIdentifier,
            String promptVersion,
            String input,
            String title,
            String priority,
            List<DraftItem> items) {
        ExtractionRecord record = new ExtractionRecord();
        record.tenantSlug = tenantSlug;
        record.schemaVersion = SCHEMA_VERSION;
        record.modelIdentifier = modelIdentifier;
        record.promptVersion = promptVersion;
        record.input = input;
        record.title = title;
        record.priority = priority;
        record.items = new ArrayList<>(items);
        record.createdAt = Instant.now();
        return record;
    }

    public String getId() {
        return id;
    }

    public String getTenantSlug() {
        return tenantSlug;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getModelIdentifier() {
        return modelIdentifier;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getInput() {
        return input;
    }

    public String getTitle() {
        return title;
    }

    public String getPriority() {
        return priority;
    }

    public List<DraftItem> getItems() {
        return items;
    }

    public void setItems(List<DraftItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public String getCreatedRequestId() {
        return createdRequestId;
    }

    public void setCreatedRequestId(String createdRequestId) {
        this.createdRequestId = createdRequestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static int currentSchemaVersion() {
        return SCHEMA_VERSION;
    }

    /** One validated draft line, embedded. Bounds mirror AiValidator. */
    public static class DraftItem {

        private String description;

        private int quantity;

        private long unitPriceMinor;

        private DraftItem() {
            // mapping only
        }

        public DraftItem(String description, int quantity, long unitPriceMinor) {
            this.description = description;
            this.quantity = quantity;
            this.unitPriceMinor = unitPriceMinor;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public long getUnitPriceMinor() {
            return unitPriceMinor;
        }

        public void setUnitPriceMinor(long unitPriceMinor) {
            this.unitPriceMinor = unitPriceMinor;
        }
    }
}
