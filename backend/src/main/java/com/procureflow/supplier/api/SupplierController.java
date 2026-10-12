package com.procureflow.supplier.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.shared.document.Filenames;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.application.ContactService;
import com.procureflow.supplier.application.PerformanceService;
import com.procureflow.supplier.application.SupplierImportService;
import com.procureflow.supplier.application.SupplierService;
import com.procureflow.supplier.domain.Supplier;
import com.procureflow.supplier.domain.SupplierCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.procureflow.shared.web.Paged;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

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
    private final SupplierImportService imports;
    private final ContactService contacts;
    private final PerformanceService performances;

    public SupplierController(
            SupplierService suppliers,
            SupplierImportService imports,
            ContactService contacts,
            PerformanceService performances) {
        this.suppliers = suppliers;
        this.imports = imports;
        this.contacts = contacts;
        this.performances = performances;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search suppliers of the current workspace (paged)")
    public ResponseEntity<Paged<SupplierResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Supplier.Status status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(suppliers
                .page(principal.tenantId(), status, query, Paged.pageOrThrow(page), Paged.sizeOrThrow(size))
                .map(SupplierResponse::from));
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

    @GetMapping("/export.xlsx")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export the filtered supplier directory as xlsx (max 5000 rows)")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Supplier.Status status,
            @RequestParam(required = false, name = "q") String query) {
        List<Supplier> rows = suppliers.exportRows(
                principal.tenantId(), status, query, principal.userId());
        List<List<Object>> body = rows.stream().map(supplier -> List.<Object>of(
                supplier.getName(),
                supplier.getStatus().name(),
                orBlank(supplier.getTaxId()),
                orBlank(supplier.getEmail()),
                orBlank(supplier.getPhone()),
                orBlank(supplier.getAddress()),
                supplier.getCategories().stream()
                        .map(SupplierCategory::getName)
                        .sorted()
                        .collect(Collectors.joining(";")),
                supplier.getCreatedAt())).toList();
        byte[] content = Spreadsheet.book(
                "Suppliers",
                List.of("name", "status", "tax_id", "email", "phone", "address", "categories", "created_at"),
                body);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.xlsx("suppliers", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('supplier:write')")
    @Operation(summary = "Bulk-create suppliers from .xlsx/.csv (all-or-nothing, max 500 rows)")
    public ResponseEntity<SupplierImportService.ImportResult> importFile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("MALFORMED_UPLOAD", "Uploaded file could not be read");
        }
        SupplierImportService.ImportResult result = imports.importFile(
                principal.tenantId(), principal.userId(), file.getOriginalFilename(), content);
        return ResponseEntity.ok(result);
    }

    private static String orBlank(String value) {
        return value == null ? "" : value;
    }
}
