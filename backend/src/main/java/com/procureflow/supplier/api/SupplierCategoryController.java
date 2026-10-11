package com.procureflow.supplier.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.supplier.application.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant category catalog. Writes need {@code supplier:write}. */
@RestController
@RequestMapping("/api/v1/supplier-categories")
@Tag(name = "supplier-categories", description = "Supplier category catalog")
public class SupplierCategoryController {

    private final CategoryService categories;

    public SupplierCategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List categories of the current workspace")
    public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(categories.list(principal.tenantId()).stream()
                .map(CategoryResponse::from)
                .toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CategoryResponse.from(
                        categories.create(principal.tenantId(), request.name(), request.description())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Rename a category")
    public ResponseEntity<CategoryResponse> rename(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(CategoryResponse.from(
                categories.rename(principal.tenantId(), id, request.name(), request.description())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Delete an unused category")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        categories.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
