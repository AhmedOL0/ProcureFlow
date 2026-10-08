package com.procureflow.organization.infrastructure.web;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.shared.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propagates the tenant of the authenticated caller into
 * {@link TenantContext} for the duration of the request. Registered after
 * authorization in the security chain, so the principal is already trusted.
 * Always clears the context afterwards: leaked tenants across pooled
 * threads would be a data-isolation incident.
 */
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                TenantContext.setTenantId(user.tenantId());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
