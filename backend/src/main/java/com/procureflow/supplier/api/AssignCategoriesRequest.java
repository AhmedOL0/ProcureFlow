package com.procureflow.supplier.api;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** Replaces the supplier's whole category set (empty set clears it). */
public record AssignCategoriesRequest(@NotNull Set<UUID> categoryIds) {
}
