package com.procureflow.budget.application;

import com.procureflow.budget.domain.Budget;
import com.procureflow.budget.domain.BudgetReservation;
import com.procureflow.budget.infrastructure.BudgetRepository;
import com.procureflow.budget.infrastructure.BudgetReservationRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import com.procureflow.audit.application.AuditTrailLogged;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget pots and in-transaction reservation. Approving a request holds its
 * total against the current UTC month's pot: budget rows lock
 * ({@code FOR UPDATE}, id-ordered so concurrent approvers serialize) and
 * overspend answers 409 before anything is written. A period with no budget
 * configured holds nothing — governance applies once a pot exists.
 */
@Service
public class BudgetService implements BudgetReservationPort {

    private static final Pattern PERIOD = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final BudgetRepository budgets;
    private final BudgetReservationRepository reservations;
    private final TenantProvisioning tenants;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meters;

    public BudgetService(
            BudgetRepository budgets,
            BudgetReservationRepository reservations,
            TenantProvisioning tenants,
            ApplicationEventPublisher events,
            MeterRegistry meters) {
        this.budgets = budgets;
        this.reservations = reservations;
        this.tenants = tenants;
        this.events = events;
        this.meters = meters;
    }

    @Override
    @Transactional
    public void reserve(String tenantSlug, UUID requestId, long amountMinor, String currency) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (reservations.findByTenantIdAndRequestId(tenantId, requestId).isPresent()) {
            throw ApiException.conflict("ALREADY_RESERVED", "This request already holds budget");
        }
        List<Budget> pots = budgets.lockPeriodBudgets(tenantId, currentPeriod());
        if (pots.isEmpty()) {
            return;
        }
        Budget target = null;
        long bestRemaining = -1;
        for (Budget pot : pots) {
            long remaining = pot.getAmountMinor() - reservations.sumReservedByBudgetId(pot.getId());
            if (remaining >= amountMinor && remaining > bestRemaining) {
                target = pot;
                bestRemaining = remaining;
            }
        }
        if (target == null) {
            meters.counter("procureflow.budget.overspends").increment();
            throw ApiException.conflict("OVERSPEND", "No budget for the period covers this amount");
        }
        if (!target.getCurrency().equalsIgnoreCase(currency)) {
            throw ApiException.conflict("CURRENCY_MISMATCH", "Request currency differs from the budget currency");
        }
        try {
            reservations.saveAndFlush(new BudgetReservation(tenantId, target.getId(), requestId, amountMinor));
            meters.counter("procureflow.budget.reservations").increment();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_RESERVED", "This request already holds budget");
        }
    }

    @Override
    @Transactional
    public void release(String tenantSlug, UUID requestId) {
        reservations
                .findByTenantIdAndRequestId(tenants.requireTenantId(tenantSlug), requestId)
                .ifPresent(reservations::delete);
    }

    @Transactional
    public Budget create(String tenantSlug, UUID actorId, String name, String period, long amountMinor,
            String currency) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("NAME_REQUIRED", "Budget name is required");
        }
        requirePeriod(period);
        if (amountMinor < 0) {
            throw ApiException.badRequest("NEGATIVE_BUDGET", "Budget amount cannot be negative");
        }
        String code = currency == null || currency.isBlank() ? "MAD" : currency.trim().toUpperCase();
        if (code.length() != 3) {
            throw ApiException.badRequest("INVALID_CURRENCY", "Currency must be a 3-letter ISO code");
        }
        try {
            Budget budget = budgets.saveAndFlush(new Budget(tenantId, name.trim(), period, amountMinor, code));
            events.publishEvent(AuditTrailLogged.now(
                    tenantSlug, actorId, "BUDGET_CREATED", "budget", budget.getId(), null,
                    Map.of("name", budget.getName(), "period", period, "amountMinor", amountMinor)));
            return budget;
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("BUDGET_EXISTS", "A budget with this name already exists for the period");
        }
    }

    @Transactional(readOnly = true)
    public List<Budget> list(String tenantSlug, String period) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (period == null) {
            return budgets.findAllByTenantIdOrderByPeriodDescNameAsc(tenantId);
        }
        requirePeriod(period);
        return budgets.findAllByTenantIdAndPeriodOrderByNameAsc(tenantId, period);
    }

    @Transactional(readOnly = true)
    public BudgetView get(String tenantSlug, UUID id) {
        return view(scoped(tenantSlug, id));
    }

    @Transactional(readOnly = true)
    public List<BudgetReservation> reservationsFor(String tenantSlug, UUID budgetId) {
        scoped(tenantSlug, budgetId);
        return reservations.findAllByBudgetIdOrderByCreatedAtDesc(budgetId);
    }

    @Transactional
    public void delete(String tenantSlug, UUID actorId, UUID id) {
        Budget budget = scoped(tenantSlug, id);
        if (reservations.existsByBudgetId(budget.getId())) {
            throw ApiException.conflict("BUDGET_IN_USE", "This budget still holds reservations");
        }
        budgets.delete(budget);
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug, actorId, "BUDGET_DELETED", "budget", budget.getId(),
                Map.of("name", budget.getName(), "period", budget.getPeriod()), null));
    }

    @Transactional(readOnly = true)
    public BudgetView view(Budget budget) {
        long reserved = reservations.sumReservedByBudgetId(budget.getId());
        return new BudgetView(
                budget.getId(),
                budget.getName(),
                budget.getPeriod(),
                budget.getAmountMinor(),
                budget.getCurrency(),
                reserved,
                budget.getAmountMinor() - reserved);
    }

    private Budget scoped(String tenantSlug, UUID id) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        return budgets
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("BUDGET_NOT_FOUND", "Budget not found"));
    }

    private static void requirePeriod(String period) {
        if (period == null || !PERIOD.matcher(period).matches()) {
            throw ApiException.badRequest("INVALID_PERIOD", "Period must be YYYY-MM");
        }
    }

    static String currentPeriod() {
        return YearMonth.from(LocalDate.now(ZoneOffset.UTC)).toString();
    }

    public record BudgetView(
            UUID id, String name, String period, long amountMinor, String currency, long reservedMinor,
            long remainingMinor) {
    }
}
