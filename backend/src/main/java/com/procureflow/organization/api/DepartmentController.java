package com.procureflow.organization.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.organization.application.DepartmentService;
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

/** Department CRUD, always scoped to the caller's workspace. */
@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "departments", description = "Workspace departments")
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    @Operation(summary = "List departments of the current workspace")
    public ResponseEntity<List<DepartmentResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                departments.list(principal.tenantId()).stream().map(DepartmentResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('department:manage')")
    @Operation(summary = "Create a department")
    public ResponseEntity<DepartmentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DepartmentResponse.from(
                        departments.create(principal.tenantId(), request.name(), request.parentId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one department of the current workspace")
    public ResponseEntity<DepartmentResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(DepartmentResponse.from(departments.scoped(principal.tenantId(), id)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    @Operation(summary = "Rename a department")
    public ResponseEntity<DepartmentResponse> rename(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(
                DepartmentResponse.from(departments.rename(principal.tenantId(), id, request.name())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    @Operation(summary = "Delete an empty department")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        departments.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
