package com.procureflow.supplier.application;

import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.domain.Supplier;
import com.procureflow.supplier.domain.SupplierContact;
import com.procureflow.supplier.infrastructure.SupplierContactRepository;
import com.procureflow.supplier.infrastructure.SupplierRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contact management inside one supplier. Setting a contact primary unsets
 * every other primary of the same supplier: the invariant holds in code,
 * not just in the UI.
 */
@Service
@Transactional
public class ContactService {

    private final SupplierContactRepository contacts;
    private final SupplierRepository suppliers;

    public ContactService(SupplierContactRepository contacts, SupplierRepository suppliers) {
        this.contacts = contacts;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public List<SupplierContact> list(String tenantSlug, UUID supplierId) {
        scopedSupplier(tenantSlug, supplierId);
        return contacts.findAllBySupplier(supplierId, tenantSlug);
    }

    public SupplierContact add(
            String tenantSlug,
            UUID supplierId,
            String name,
            String email,
            String phone,
            String title,
            boolean primary) {
        Supplier supplier = scopedSupplier(tenantSlug, supplierId);
        SupplierContact contact = new SupplierContact(supplier, name);
        contact.setEmail(email);
        contact.setPhone(phone);
        contact.setTitle(title);
        if (primary) {
            unsetPrimaries(supplierId, null);
        }
        contact.setPrimary(primary);
        return contacts.save(contact);
    }

    public SupplierContact update(
            String tenantSlug,
            UUID contactId,
            String name,
            String email,
            String phone,
            String title,
            Boolean primary) {
        SupplierContact contact = scoped(tenantSlug, contactId);
        if (name != null) {
            contact.setName(name);
        }
        if (email != null) {
            contact.setEmail(email);
        }
        if (phone != null) {
            contact.setPhone(phone);
        }
        if (title != null) {
            contact.setTitle(title);
        }
        if (primary != null) {
            if (primary) {
                unsetPrimaries(contact.getSupplier().getId(), contact.getId());
            }
            contact.setPrimary(primary);
        }
        return contacts.save(contact);
    }

    public void remove(String tenantSlug, UUID contactId) {
        contacts.delete(scoped(tenantSlug, contactId));
    }

    /**
     * Clears the primary flag on every other contact in one statement. Safe
     * for both callers: neither holds other contacts in the session (add
     * works on a new instance, update on the excluded one), so no managed
     * copy can go stale behind the bulk update.
     */
    private void unsetPrimaries(UUID supplierId, UUID exceptId) {
        contacts.unsetPrimaries(supplierId, exceptId);
    }

    private Supplier scopedSupplier(String tenantSlug, UUID supplierId) {
        return suppliers
                .findByIdAndTenantSlug(supplierId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "Supplier not found"));
    }

    private SupplierContact scoped(String tenantSlug, UUID contactId) {
        return contacts
                .findByIdAndTenantSlug(contactId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("CONTACT_NOT_FOUND", "Contact not found"));
    }
}
