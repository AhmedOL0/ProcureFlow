package com.procureflow.dev;

import com.procureflow.approval.application.ApprovalService;
import com.procureflow.approval.application.WorkflowService;
import com.procureflow.budget.application.BudgetService;
import com.procureflow.identity.application.AuthService;
import com.procureflow.identity.application.Registration;
import com.procureflow.identity.application.UserAdminService;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.invoice.application.InvoiceService;
import com.procureflow.organization.application.DepartmentService;
import com.procureflow.organization.application.MembershipService;
import com.procureflow.procurement.application.PurchaseRequestService;
import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.purchaseorder.application.OrderService;
import com.procureflow.supplier.application.CategoryService;
import com.procureflow.supplier.application.ContactService;
import com.procureflow.supplier.application.PerformanceService;
import com.procureflow.supplier.application.SupplierService;
import com.procureflow.supplier.domain.Supplier;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo workspace seed for local visualization and manual testing. Dev
 * profile only — never active in tests or production. Builds the
 * {@code demo} workspace entirely through application services so every
 * invariant (budgets, approvals, 3-way match, audit trail, notifications)
 * holds exactly as if a team had worked for six months, then shifts
 * historical chains back in time with plain timestamp updates so the
 * spend trajectory spans 2026-05..2026-10 instead of a single point.
 *
 * <p>Run once against a migrated database (mail must stay suppressed):
 * <pre>
 * MAIL_PROVIDER=log SPRING_PROFILES_ACTIVE=dev docker compose run --rm backend
 * </pre>
 * Idempotent: returns immediately when the demo admin already exists. A
 * half-seeded workspace is resumed only by resetting first
 * ({@code DELETE FROM tenants WHERE slug = 'demo'} cascades).
 */
@Component
@Profile("dev")
@Order(Integer.MAX_VALUE)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private static final String SLUG = "demo";
    private static final String PASSWORD = "ProcureFlow-2026!";
    private static final String CURRENCY = "MAD";

    private final AuthService auth;
    private final UserAdminService users;
    private final UserRepository userRows;
    private final DepartmentService departments;
    private final MembershipService memberships;
    private final CategoryService categories;
    private final SupplierService suppliers;
    private final ContactService contacts;
    private final PerformanceService performances;
    private final BudgetService budgets;
    private final WorkflowService workflows;
    private final PurchaseRequestService requests;
    private final ApprovalService approvals;
    private final OrderService orders;
    private final InvoiceService invoices;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @SuppressWarnings("java:S107")
    public DemoSeeder(
            AuthService auth,
            UserAdminService users,
            UserRepository userRows,
            DepartmentService departments,
            MembershipService memberships,
            CategoryService categories,
            SupplierService suppliers,
            ContactService contacts,
            PerformanceService performances,
            BudgetService budgets,
            WorkflowService workflows,
            PurchaseRequestService requests,
            ApprovalService approvals,
            OrderService orders,
            InvoiceService invoices,
            JdbcTemplate jdbc,
            TransactionTemplate transactions) {
        this.auth = auth;
        this.users = users;
        this.userRows = userRows;
        this.departments = departments;
        this.memberships = memberships;
        this.categories = categories;
        this.suppliers = suppliers;
        this.contacts = contacts;
        this.performances = performances;
        this.budgets = budgets;
        this.workflows = workflows;
        this.requests = requests;
        this.approvals = approvals;
        this.orders = orders;
        this.invoices = invoices;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRows.findByTenantSlugAndEmail(SLUG, "admin@demo.test").isPresent()) {
            log.info("Demo workspace already seeded, skipping");
            return;
        }
        log.info("Seeding demo workspace");

        Map<String, UUID> people = seedPeople();
        log.info("Demo seed: people done");
        UUID admin = people.get("admin@demo.test");
        UUID approver = people.get("approver@demo.test");
        UUID member = people.get("member@demo.test");
        UUID officer = people.get("officer@demo.test");
        UUID finance = people.get("finance@demo.test");

        seedOrganization(people);
        log.info("Demo seed: organization done");
        Map<String, UUID> vendors = seedSuppliers();
        log.info("Demo seed: suppliers done");
        seedBudgets(finance);
        log.info("Demo seed: budgets done");
        workflows.addStep(
                SLUG,
                workflows.create(SLUG, "Standard lane", 0L, null, 7).getId(),
                1,
                approver);

        // Live tail: drafts, submitted and freshly approved rows stay in the
        // current month so the demo walkthrough (approve → order → invoice →
        // pay) works against them.
        SeedRequest fleet = draft(member, "Demo workstation fleet", PurchaseRequest.Priority.HIGH, List.of(
                item("Laptop 16in", "Hardware", 15, 120_000, vendors.get("Bechtle")),
                item("Docking station", "Hardware", 15, 25_000, vendors.get("Bechtle"))));
        submit(fleet.id());
        SeedRequest cloud = draft(officer, "Cloud credits Q4", PurchaseRequest.Priority.URGENT, List.of(
                item("Cloud credits", "Cloud", 1, 900_000, vendors.get("CloudNine")),
                item("Support package", "Software", 1, 150_000, vendors.get("CloudNine"))));
        submit(cloud.id());
        SeedRequest chairs = draft(member, "Office chairs refresh", PurchaseRequest.Priority.MEDIUM, List.of(
                item("Ergonomic chair", "Office", 12, 45_000, vendors.get("OfficePlus")),
                item("Standing desk", "Office", 6, 80_000, vendors.get("OfficePlus"))));
        submit(chairs.id());
        decide(chairs.id(), approver, true, "Fits the Q4 furniture plan");
        draft(member, "Team offsite catering", PurchaseRequest.Priority.LOW,
                List.of(item("Catering", "Office", 1, 200_000, null)));
        draft(member, "Spare monitors", PurchaseRequest.Priority.MEDIUM,
                List.of(item("Monitor 27in", "Hardware", 8, 60_000, vendors.get("Bechtle"))));
        log.info("Demo seed: live requests done");

        // Historical chains: approved (and decided) now, backdated after so
        // reservations and validations run against live data.
        SeedRequest servers = decided(member, approver, "Server refresh", PurchaseRequest.Priority.HIGH, true,
                "Capacity signed off", List.of(
                        item("Server node", "Hardware", 2, 450_000, vendors.get("Bechtle")),
                        item("Rack PDU", "Hardware", 4, 30_000, vendors.get("Bechtle"))));
        SeedRequest network = decided(officer, approver, "Network upgrade", PurchaseRequest.Priority.HIGH, true,
                "Approved", List.of(
                        item("Access switch", "Hardware", 6, 95_000, vendors.get("CloudNine")),
                        item("Cabling bundle", "Office", 1, 120_000, vendors.get("OfficePlus"))));
        SeedRequest licenses = decided(admin, approver, "Design licenses", PurchaseRequest.Priority.MEDIUM, true,
                "Approved", List.of(
                        item("Design seat", "Software", 20, 18_000, vendors.get("CloudNine")),
                        item("Font pack", "Software", 1, 40_000, null)));
        SeedRequest desks = decided(admin, approver, "Warehouse desks", PurchaseRequest.Priority.LOW, true,
                "Approved", List.of(
                        item("Work desk", "Office", 10, 55_000, vendors.get("OfficePlus"))));
        SeedRequest flights = decided(officer, approver, "Partner flights", PurchaseRequest.Priority.MEDIUM, false,
                "Duplicate of the July plan", List.of(
                        item("Flight", "Travel", 4, 22_000, vendors.get("LogiFreight"))));
        SeedRequest offsite = decided(admin, approver, "Winter offsite venue", PurchaseRequest.Priority.MEDIUM, false,
                "Over the quarterly event budget", List.of(
                        item("Venue booking", "Travel", 1, 350_000, vendors.get("LogiFreight"))));
        SeedRequest keyboards = decided(admin, approver, "Spare peripherals", PurchaseRequest.Priority.LOW, true,
                "Approved", List.of(
                        item("Keyboard", "Hardware", 30, 8_000, vendors.get("Bechtle")),
                        item("Mouse", "Hardware", 30, 4_000, vendors.get("Bechtle"))));
        requests.cancel(SLUG, keyboards.id(), admin, true);

        UUID serversOrder = order(admin, servers.id(), vendors.get("Bechtle"));
        receiveAll(admin, serversOrder);
        invoice(admin, serversOrder, "INV-2026-0501", true, 1, 1);
        UUID networkOrder = order(admin, network.id(), vendors.get("CloudNine"));
        receiveAll(admin, networkOrder);
        invoice(admin, networkOrder, "INV-2026-0601", false, 2, 5);
        UUID licensesOrder = order(admin, licenses.id(), vendors.get("OfficePlus"));
        receiveAll(admin, licensesOrder);
        // Sent but untouched: the middle of the fulfillment pipeline.
        order(admin, desks.id(), vendors.get("OfficePlus"));
        log.info("Demo seed: orders and invoices done");

        backdate(Map.of(
                servers.id(), YearMonth.of(2026, 5),
                network.id(), YearMonth.of(2026, 6),
                flights.id(), YearMonth.of(2026, 6),
                licenses.id(), YearMonth.of(2026, 7),
                desks.id(), YearMonth.of(2026, 8),
                keyboards.id(), YearMonth.of(2026, 8),
                offsite.id(), YearMonth.of(2026, 9)));
        log.info("Demo workspace seeded");
    }

    private Map<String, UUID> seedPeople() {
        auth.register(
                new Registration("admin@demo.test", PASSWORD, "Ada", "Admin", SLUG, "Demo workspace", null),
                "127.0.0.1");
        users.create(SLUG, "approver@demo.test", PASSWORD, "Omar", "Approver", Set.of("APPROVER"));
        users.create(SLUG, "finance@demo.test", PASSWORD, "Meryem", "Finance", Set.of("FINANCE"));
        users.create(SLUG, "officer@demo.test", PASSWORD, "Yassine", "Officer", Set.of("OFFICER"));
        users.create(SLUG, "auditor@demo.test", PASSWORD, "Salma", "Auditor", Set.of("AUDITOR"));
        users.create(SLUG, "member@demo.test", PASSWORD, "Karim", "Member", Set.of("MEMBER"));
        // Registration mails stay suppressed on the seed run (MAIL_PROVIDER=log);
        // mark every mailbox verified so password-reset demos work too.
        Map<String, UUID> ids = new java.util.LinkedHashMap<>();
        for (String email : List.of(
                "admin@demo.test", "approver@demo.test", "finance@demo.test",
                "officer@demo.test", "auditor@demo.test", "member@demo.test")) {
            User user = userRows
                    .findByTenantSlugAndEmail(SLUG, email)
                    .orElseThrow(() -> new IllegalStateException("Seeded user missing: " + email));
            user.setVerified(true);
            userRows.save(user);
            ids.put(email, user.getId());
        }
        return ids;
    }

    private void seedOrganization(Map<String, UUID> people) {
        UUID engineering = departments.create(SLUG, "Engineering", null).getId();
        UUID operations = departments.create(SLUG, "Operations", null).getId();
        UUID finance = departments.create(SLUG, "Finance", null).getId();
        memberships.add(SLUG, people.get("member@demo.test"), engineering);
        memberships.add(SLUG, people.get("officer@demo.test"), operations);
        memberships.add(SLUG, people.get("approver@demo.test"), operations);
        memberships.add(SLUG, people.get("finance@demo.test"), finance);
    }

    private Map<String, UUID> seedSuppliers() {
        for (String name : List.of("Hardware", "Software", "Cloud", "Office", "Travel")) {
            categories.create(SLUG, name, "Demo catalog");
        }
        Map<String, UUID> catalog = new java.util.LinkedHashMap<>();
        for (var category : categories.list(SLUG)) {
            catalog.put(category.getName(), category.getId());
        }
        Map<String, UUID> vendors = new java.util.LinkedHashMap<>();
        vendors.put("Bechtle", suppliers
                .create(SLUG, "Bechtle Demo AG", "DE-123", "sales@bechtle.test", "+212600000001", "Casa")
                .getId());
        vendors.put("CloudNine", suppliers
                .create(SLUG, "CloudNine", null, "hello@cloudnine.test", null, null).getId());
        vendors.put("OfficePlus", suppliers
                .create(SLUG, "OfficePlus", null, "desk@officeplus.test", null, null).getId());
        vendors.put("TechSupply", suppliers
                .create(SLUG, "TechSupply", null, null, null, null).getId());
        vendors.put("LogiFreight", suppliers
                .create(SLUG, "LogiFreight", null, null, null, null).getId());
        contacts.add(SLUG, vendors.get("Bechtle"), "Lina Bechtle", "lina@bechtle.test", "+212600000002",
                "Account manager", true);
        contacts.add(SLUG, vendors.get("CloudNine"), "Adam Cloud", "adam@cloudnine.test", null, "Sales", true);
        contacts.add(SLUG, vendors.get("OfficePlus"), "Sara Desk", "sara@officeplus.test", null, "Sales", true);
        suppliers.assignCategories(SLUG, vendors.get("Bechtle"),
                Set.of(catalog.get("Hardware")));
        suppliers.assignCategories(SLUG, vendors.get("CloudNine"),
                Set.of(catalog.get("Cloud"), catalog.get("Software")));
        suppliers.assignCategories(SLUG, vendors.get("OfficePlus"),
                Set.of(catalog.get("Office")));
        suppliers.assignCategories(SLUG, vendors.get("TechSupply"),
                Set.of(catalog.get("Hardware")));
        suppliers.assignCategories(SLUG, vendors.get("LogiFreight"),
                Set.of(catalog.get("Travel")));
        scorecards(vendors.get("Bechtle"), new double[] {92, 88, 95, 90, 94, 96});
        scorecards(vendors.get("CloudNine"), new double[] {0, 0, 90, 93, 89, 95});
        scorecards(vendors.get("OfficePlus"), new double[] {85, 91, 87, 92, 88, 90});
        scorecards(vendors.get("TechSupply"), new double[] {0, 0, 0, 78, 82, 80});
        scorecards(vendors.get("LogiFreight"), new double[] {0, 0, 0, 0, 86, 91});
        suppliers.update(SLUG, vendors.get("TechSupply"), null, null, null, null, null, Supplier.Status.INACTIVE);
        suppliers.update(SLUG, vendors.get("LogiFreight"), null, null, null, null, null, Supplier.Status.SUSPENDED);
        return vendors;
    }

    private void scorecards(UUID supplierId, double[] rates) {
        for (int month = 5; month <= 10; month++) {
            double rate = rates[month - 5];
            if (rate <= 0) {
                continue;
            }
            performances.record(SLUG, supplierId, "2026-%02d".formatted(month),
                    BigDecimal.valueOf(rate), BigDecimal.valueOf(Math.min(99, rate + 2)), "Demo scorecard");
        }
    }

    private void seedBudgets(UUID finance) {
        for (int month = 5; month <= 9; month++) {
            budgets.create(SLUG, finance, "Operations", "2026-%02d".formatted(month), 8_000_000L, CURRENCY);
        }
        // Current-month pots stay generous: every approval below reserves
        // here at seed time, before history is backdated away.
        budgets.create(SLUG, finance, "Operations", "2026-10", 50_000_000L, CURRENCY);
        budgets.create(SLUG, finance, "Engineering", "2026-10", 20_000_000L, CURRENCY);
    }

    private PurchaseRequestService.ItemInput item(
            String description, String category, int quantity, long unitPriceMinor, UUID supplierId) {
        return new PurchaseRequestService.ItemInput(
                description, category, quantity, unitPriceMinor, CURRENCY, supplierId);
    }

    private record SeedRequest(UUID id) {
    }

    private SeedRequest draft(UUID requester, String title, PurchaseRequest.Priority priority,
            List<PurchaseRequestService.ItemInput> items) {
        return new SeedRequest(requests
                .creation(SLUG, requester, "demo-" + UUID.randomUUID(), title, title + " (demo data)",
                        priority, items)
                .request()
                .getId());
    }

    private void submit(UUID requestId) {
        var request = requests.get(SLUG, requestId);
        requests.submit(SLUG, requestId, request.getRequester().getId(), false);
        approvals.attach(SLUG, requestId);
    }

    private SeedRequest decided(UUID requester, UUID decider, String title, PurchaseRequest.Priority priority,
            boolean approved, String comment, List<PurchaseRequestService.ItemInput> items) {
        SeedRequest seeded = draft(requester, title, priority, items);
        submit(seeded.id());
        decide(seeded.id(), decider, approved, comment);
        return seeded;
    }

    private void decide(UUID requestId, UUID decider, boolean approved, String comment) {
        approvals.decide(SLUG, requestId, decider, true, approved, comment);
    }

    private UUID order(UUID actor, UUID requestId, UUID supplierId) {
        UUID orderId = orders.create(SLUG, actor, requestId, supplierId).getId();
        orders.send(SLUG, actor, orderId);
        return orderId;
    }

    private void receiveAll(UUID actor, UUID orderId) {
        orders.receive(SLUG, actor, orderId, orders.lines(SLUG, orderId).stream()
                .map(line -> new OrderService.Receipt(line.getId(), line.getQuantity()))
                .toList());
    }

    private void invoice(UUID actor, UUID orderId, String number, boolean payFull, int numerator, int denominator) {
        List<InvoiceService.LineInput> lines = orders.lines(SLUG, orderId).stream()
                .map(line -> new InvoiceService.LineInput(line.getId(), line.getQuantity()))
                .toList();
        UUID invoiceId = invoices.create(SLUG, actor, orderId, number, lines).getId();
        var created = invoices.get(SLUG, invoiceId);
        if (payFull) {
            invoices.pay(SLUG, actor, invoiceId, created.getTotalMinor());
        } else {
            invoices.pay(SLUG, actor, invoiceId, Math.max(1, created.getTotalMinor() * numerator / denominator));
        }
    }

    /**
     * Shifts historical chains (requests, items, decisions, orders, invoices,
     * payments) into their demo months and pulls the audit rows along by
     * entity id. Services alone can only stamp "now", so history needs this
     * explicit, reviewed step — business rules all ran before it.
     */
    private void backdate(Map<UUID, YearMonth> months) {
        UUID tenantId = jdbc.queryForObject("SELECT id FROM tenants WHERE slug = ?", UUID.class, SLUG);
        transactions.executeWithoutResult(status -> {
            for (Map.Entry<UUID, YearMonth> entry : months.entrySet()) {
                Timestamp requestAt = stamp(entry.getValue(), 8);
                Timestamp decidedAt = stamp(entry.getValue(), 10);
                Timestamp orderedAt = stamp(entry.getValue(), 12);
                Timestamp invoicedAt = stamp(entry.getValue(), 18);
                Timestamp paidAt = stamp(entry.getValue(), 21);
                UUID requestId = entry.getKey();
                jdbc.update("UPDATE purchase_requests SET created_at = ? WHERE id = ?", requestAt, requestId);
                jdbc.update("UPDATE purchase_request_items SET created_at = ? WHERE request_id = ?",
                        requestAt, requestId);
                jdbc.update("UPDATE approval_decisions SET created_at = ? WHERE request_id = ?",
                        decidedAt, requestId);
                jdbc.update("UPDATE purchase_orders SET created_at = ? WHERE request_id = ?", orderedAt, requestId);
                jdbc.update(
                        "UPDATE purchase_order_items SET created_at = ? WHERE order_id IN "
                                + "(SELECT id FROM purchase_orders WHERE request_id = ?)",
                        orderedAt, requestId);
                jdbc.update(
                        "UPDATE invoices SET created_at = ? WHERE order_id IN "
                                + "(SELECT id FROM purchase_orders WHERE request_id = ?)",
                        invoicedAt, requestId);
                jdbc.update(
                        "UPDATE invoice_lines SET created_at = ? WHERE invoice_id IN "
                                + "(SELECT id FROM invoices WHERE order_id IN "
                                + "(SELECT id FROM purchase_orders WHERE request_id = ?))",
                        invoicedAt, requestId);
                jdbc.update(
                        "UPDATE invoice_payments SET created_at = ? WHERE invoice_id IN "
                                + "(SELECT id FROM invoices WHERE order_id IN "
                                + "(SELECT id FROM purchase_orders WHERE request_id = ?))",
                        paidAt, requestId);
            }
            jdbc.update(
                    "UPDATE audit_events a SET created_at = COALESCE("
                            + "(SELECT r.created_at FROM purchase_requests r WHERE r.id = a.entity_id),"
                            + "(SELECT o.created_at FROM purchase_orders o WHERE o.id = a.entity_id),"
                            + "(SELECT i.created_at FROM invoices i WHERE i.id = a.entity_id),"
                            + "a.created_at) WHERE a.tenant_id = ? AND a.action <> 'BUDGET_CREATED'",
                    tenantId);
            jdbc.update(
                    "UPDATE audit_events a SET created_at = to_timestamp(b.period, 'YYYY-MM') "
                            + "FROM budgets b WHERE b.id = a.entity_id AND a.action = 'BUDGET_CREATED' "
                            + "AND a.tenant_id = ?",
                    tenantId);
        });
    }

    private static Timestamp stamp(YearMonth month, int day) {
        return Timestamp.from(LocalDate.of(month.getYear(), month.getMonth(), Math.min(day, month.lengthOfMonth()))
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC));
    }
}
