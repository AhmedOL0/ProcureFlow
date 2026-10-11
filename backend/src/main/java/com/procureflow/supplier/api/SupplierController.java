package com.procureflow.supplier.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.supplier.application.ContactService;
import com.procureflow.supplier.application.PerformanceService;
import com.procureflow.supplier.application.SupplierService;
import com.procureflow.supplier.domain.Supplier;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suppliers plus their nested contacts, categories and scorecards.
 * Reads need any authenticated tenant member; writes need
 * {@code supplier:write}.
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "suppliers", description = "Supplier management")
public class SupplierController {

    private final SupplierService suppliers;
    private final ContactService contacts;
    private final PerformanceService performances;

    public SupplierController(
            SupplierService suppliers, ContactService contacts, PerformanceService performances) {
        this.suppliers = suppliers;
        this.contacts = contacts;
        this.performances = performances;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search suppliers of the current workspace")
    public ResponseEntity<List<SupplierResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Supplier.Status status,
            @RequestParam(required = false, name = "q") String query) {
        return ResponseEntity.ok(suppliers.search(principal.tenantId(), status, query).stream()
                .map(SupplierResponse::from)
                .toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Create a supplier")
    public ResponseEntity<SupplierResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SupplierResponse.from(suppliers.create(
                        principal.tenantId(),
                        request.name(),
                        request.taxId(),
                        request.email(),
                        request.phone(),
                        request.address())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get one supplier of the current workspace")
    public ResponseEntity<SupplierResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(SupplierResponse.from(suppliers.get(principal.tenantId(), id)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Update a supplier (null fields stay unchanged)")
    public ResponseEntity<SupplierResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(SupplierResponse.from(suppliers.update(
                principal.tenantId(),
                id,
                request.name(),
                request.taxId(),
                request.email(),
                request.phone(),
                request.address(),
                request.status())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Delete a supplier and its contacts/scorecards")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        suppliers.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/contacts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List contacts of a supplier")
    public ResponseEntity<List<ContactResponse>> contacts(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(contacts.list(principal.tenantId(), id).stream()
                .map(ContactResponse::from)
                .toList());
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Add a contact to a supplier")
    public ResponseEntity<ContactResponse> addContact(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ContactResponse.from(contacts.add(
                        principal.tenantId(),
                        id,
                        request.name(),
                        request.email(),
                        request.phone(),
                        request.title(),
                        request.primary())));
    }

    @GetMapping("/{id}/categories")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List categories assigned to a supplier")
    public ResponseEntity<List<CategoryResponse>> categories(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(suppliers.categoriesOf(principal.tenantId(), id).stream()
                .map(CategoryResponse::from)
                .toList());
    }

    @PostMapping("/{id}/categories")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Replace the category set of a supplier")
    public ResponseEntity<List<CategoryResponse>> assignCategories(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignCategoriesRequest request) {
        return ResponseEntity.ok(suppliers
                .assignCategories(principal.tenantId(), id, request.categoryIds()).stream()
                .map(CategoryResponse::from)
                .toList());
    }

    @GetMapping("/{id}/performances")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List scorecards of a supplier, newest first")
    public ResponseEntity<List<PerformanceResponse>> performances(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(performances.list(principal.tenantId(), id).stream()
                .map(PerformanceResponse::from)
                .toList());
    }

    @PostMapping("/{id}/performances")
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Record a monthly scorecard")
    public ResponseEntity<PerformanceResponse> recordPerformance(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody RecordPerformanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PerformanceResponse.from(performances.record(
                        principal.tenantId(),
                        id,
                        request.period(),
                        request.onTimeRate(),
                        request.qualityScore(),
                        request.notes())));
    }
}
