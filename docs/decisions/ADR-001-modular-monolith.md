# ADR-001: Modular monolith instead of microservices

- Status: Accepted (Phase 1)
- Context: Small team, one deployable, deep procurement domain with
  transactions spanning requests, budgets and approvals.
- Decision: One Spring Boot deployable organized as strict business
  modules with ArchUnit-enforced boundaries.
- Consequences: No network hops or distributed transactions; module
  extraction stays possible later because boundaries are compile-guarded.
  Discipline risk: boundaries rot without the ArchUnit gate, so the gate
  runs on every build.
