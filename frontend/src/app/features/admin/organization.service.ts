import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Tenant = components['schemas']['TenantResponse'];
export type Department = components['schemas']['DepartmentResponse'];
export type Membership = components['schemas']['MembershipResponse'];
export type WorkspaceUser = components['schemas']['UserResponse'];
export type AuditEvent = components['schemas']['AuditEventResponse'];
export type WorkspaceInvite = components['schemas']['InviteResponse'];

/**
 * Thin typed clients over organization and audit contracts. Role assignment
 * happens at user creation only — the backend exposes no role-update
 * endpoint, so this UI does not simulate one.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  tenant(): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.base}/tenants/current`);
  }

  renameTenant(name: string): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.base}/tenants/current`, { name });
  }

  departments(
    query: string | null,
    pageIndex: number,
    pageSize: number,
  ): Observable<{ rows: Department[]; total: number }> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (query?.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http
      .get<components['schemas']['PagedDepartmentResponse']>(`${this.base}/departments`, { params })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  createDepartment(name: string): Observable<Department> {
    return this.http.post<Department>(`${this.base}/departments`, { name });
  }

  renameDepartment(id: string, name: string): Observable<Department> {
    return this.http.patch<Department>(`${this.base}/departments/${id}`, { name });
  }

  deleteDepartment(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/departments/${id}`);
  }

  memberships(
    pageIndex: number,
    pageSize: number,
  ): Observable<{ rows: Membership[]; total: number }> {
    const params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    return this.http
      .get<components['schemas']['PagedMembershipResponse']>(`${this.base}/memberships`, { params })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  addMembership(userId: string, departmentId: string): Observable<Membership> {
    return this.http.post<Membership>(`${this.base}/memberships`, { userId, departmentId });
  }

  removeMembership(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/memberships/${id}`);
  }

  users(
    query: string | null,
    pageIndex: number,
    pageSize: number,
  ): Observable<{ rows: WorkspaceUser[]; total: number }> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (query?.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http
      .get<components['schemas']['PagedUserResponse']>(`${this.base}/users`, { params })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  createUser(body: components['schemas']['CreateUserRequest']): Observable<WorkspaceUser> {
    return this.http.post<WorkspaceUser>(`${this.base}/users`, body);
  }

  updateProfile(id: string, firstName: string | null, lastName: string | null): Observable<WorkspaceUser> {
    return this.http.patch<WorkspaceUser>(`${this.base}/users/${id}`, { firstName, lastName });
  }

  resetPassword(id: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.base}/users/${id}/password`, { password });
  }

  invites(): Observable<WorkspaceInvite[]> {
    return this.http.get<WorkspaceInvite[]>(`${this.base}/invites`);
  }

  invite(email: string, roleNames: string[]): Observable<WorkspaceInvite> {
    return this.http.post<WorkspaceInvite>(`${this.base}/invites`, { email, roleNames });
  }

  revokeInvite(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/invites/${id}`);
  }
}

export interface AuditPage {
  rows: AuditEvent[];
  total: number;
}

/** Append-only trail reads, paged server-side. The backend offers no mutation endpoints. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  page(
    entityType: string | null,
    entityId: string | null,
    pageIndex: number,
    pageSize: number,
  ): Observable<AuditPage> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (entityType) {
      params = params.set('entityType', entityType);
    }
    if (entityId) {
      params = params.set('entityId', entityId);
    }
    return this.http
      .get<components['schemas']['PagedAuditEventResponse']>(`${this.base}/audit-events`, { params })
      .pipe(
        map((result) => ({
          rows: result?.content ?? [],
          total: result?.totalElements ?? 0,
        })),
      );
  }
}
