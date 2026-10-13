package com.procureflow.budget.api;

import com.procureflow.budget.application.BudgetImportService;
import com.procureflow.budget.application.BudgetService;
import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.shared.document.Filenames;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.procureflow.shared.web.Paged;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Budget pots and their reservations. Approving a request reserves its total
 * in the same transaction as the status change; overspend answers 409.
 * Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "budgets", description = "Monthly spending pots and approval reservations")
public class BudgetController {

    private final BudgetService budgets;
    private final BudgetImportService imports;

    public BudgetController(BudgetService budgets, BudgetImportService imports) {
        this.budgets = budgets;
        this.imports = imports;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('budget:manage')")
    @Operation(summary = "Open a named pot for one month (YYYY-MM)")
    public ResponseEntity<BudgetResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateBudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BudgetResponse.from(budgets.view(budgets.create(
                principal.tenantId(),
                principal.userId(),
                request.name(),
                request.period(),
                request.amountMinor(),
                request.currency()))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "Paged pots of the current workspace, newest period first")
    public ResponseEntity<Paged<BudgetResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String period,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(budgets
                .pagedViews(
                        principal.tenantId(), period, query, Paged.pageOrThrow(page), Paged.sizeOrThrow(size))
                .map(BudgetResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "One pot with its reserved and remaining amounts")
    public ResponseEntity<BudgetResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(BudgetResponse.from(budgets.get(principal.tenantId(), id)));
    }

    @GetMapping("/{id}/reservations")
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "Holds currently resting on one pot")
    public ResponseEntity<List<ReservationResponse>> reservations(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(budgets.reservationsFor(principal.tenantId(), id).stream()
                .map(ReservationResponse::from)
                .toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('budget:manage')")
    @Operation(summary = "Close a pot (refused while holds rest on it)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        budgets.delete(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "Export filtered pots as xlsx (max 5000 rows)")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String period,
            @RequestParam(required = false, name = "q") String query) {
        List<List<Object>> body = budgets
                .exportRows(principal.tenantId(), period, query, principal.userId())
                .stream()
                .map(pot -> List.<Object>of(
                        pot.name(),
                        pot.period(),
                        pot.amountMinor() / 100.0,
                        pot.currency(),
                        pot.reservedMinor() / 100.0,
                        pot.remainingMinor() / 100.0))
                .toList();
        byte[] content = Spreadsheet.book(
                "Budgets",
                List.of("name", "period", "amount", "currency", "reserved", "remaining"),
                body);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.xlsx("budgets", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('budget:manage')")
    @Operation(summary = "Bulk-open pots from .xlsx/.csv (all-or-nothing, max 500 rows)")
    public ResponseEntity<BudgetImportService.ImportResult> importFile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("MALFORMED_UPLOAD", "Uploaded file could not be read");
        }
        return ResponseEntity.ok(imports.importFile(
                principal.tenantId(), principal.userId(), file.getOriginalFilename(), content));
    }
}
