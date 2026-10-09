package com.procureflow.supplier.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.supplier.application.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct contact edits (listing/creation live nested under suppliers). */
@RestController
@RequestMapping("/api/v1/supplier-contacts")
@Tag(name = "supplier-contacts", description = "Direct contact edits")
@PreAuthorize("hasAuthority('supplier:write')")
public class SupplierContactController {

    private final ContactService contacts;

    public SupplierContactController(ContactService contacts) {
        this.contacts = contacts;
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a contact (null fields stay unchanged)")
    public ResponseEntity<ContactResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContactRequest request) {
        return ResponseEntity.ok(ContactResponse.from(contacts.update(
                principal.tenantId(),
                id,
                request.name(),
                request.email(),
                request.phone(),
                request.title(),
                request.primary())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a contact")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        contacts.remove(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
