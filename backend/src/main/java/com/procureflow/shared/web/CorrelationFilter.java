package com.procureflow.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request correlation: every response carries {@code X-Request-Id} (echoing
 * a caller-provided value when present, else a fresh UUID), and the id rides
 * the logging MDC as {@code traceId} — the same value the error envelope
 * reports, so a client traceId pinpoints its server log lines. Runs before
 * security so denied requests are correlatable too; always clears the MDC
 * (same leak discipline as TenantContext).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        String traceId = header == null || header.isBlank() ? UUID.randomUUID().toString() : header.trim();
        MDC.put(MDC_KEY, traceId);
        try {
            response.setHeader(HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
