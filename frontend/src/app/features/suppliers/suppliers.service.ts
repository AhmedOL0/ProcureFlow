import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Supplier = components['schemas']['SupplierResponse'];
export type SupplierContact = components['schemas']['ContactResponse'];
export type SupplierCategory = components['schemas']['CategoryResponse'];
export type SupplierPerformance = components['schemas']['PerformanceResponse'];

/**
 * Thin typed client over the supplier contract — no duplicated business
 * rules. The single-primary invariant, search semantics and reference
 * guards all live server-side; components only render outcomes.
 */
@Injectable({ providedIn: 'root' })
export class SuppliersService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  search(status: string | null, query: string | null): Observable<Supplier[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    if (query?.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<Supplier[]>(`${this.base}/suppliers`, { params });
  }

  get(id: string): Observable<Supplier> {
    return this.http.get<Supplier>(`${this.base}/suppliers/${id}`);
  }

  create(body: components['schemas']['CreateSupplierRequest']): Observable<Supplier> {
    return this.http.post<Supplier>(`${this.base}/suppliers`, body);
  }

  update(id: string, body: components['schemas']['UpdateSupplierRequest']): Observable<Supplier> {
    return this.http.patch<Supplier>(`${this.base}/suppliers/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/suppliers/${id}`);
  }

  contacts(supplierId: string): Observable<SupplierContact[]> {
    return this.http.get<SupplierContact[]>(`${this.base}/suppliers/${supplierId}/contacts`);
  }

  addContact(supplierId: string, body: components['schemas']['AddContactRequest']): Observable<SupplierContact> {
    return this.http.post<SupplierContact>(`${this.base}/suppliers/${supplierId}/contacts`, body);
  }

  updateContact(
    contactId: string,
    body: components['schemas']['UpdateContactRequest'],
  ): Observable<SupplierContact> {
    return this.http.patch<SupplierContact>(`${this.base}/supplier-contacts/${contactId}`, body);
  }

  removeContact(contactId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/supplier-contacts/${contactId}`);
  }

  categoriesOf(supplierId: string): Observable<SupplierCategory[]> {
    return this.http.get<SupplierCategory[]>(`${this.base}/suppliers/${supplierId}/categories`);
  }

  catalog(): Observable<SupplierCategory[]> {
    return this.http.get<SupplierCategory[]>(`${this.base}/supplier-categories`);
  }

  createCategory(name: string, description?: string): Observable<SupplierCategory> {
    return this.http.post<SupplierCategory>(`${this.base}/supplier-categories`, { name, description });
  }

  assignCategories(supplierId: string, categoryIds: string[]): Observable<SupplierCategory[]> {
    return this.http.post<SupplierCategory[]>(`${this.base}/suppliers/${supplierId}/categories`, {
      categoryIds,
    });
  }

  performances(supplierId: string): Observable<SupplierPerformance[]> {
    return this.http.get<SupplierPerformance[]>(`${this.base}/suppliers/${supplierId}/performances`);
  }

  recordPerformance(
    supplierId: string,
    body: components['schemas']['RecordPerformanceRequest'],
  ): Observable<SupplierPerformance> {
    return this.http.post<SupplierPerformance>(`${this.base}/suppliers/${supplierId}/performances`, body);
  }
}
