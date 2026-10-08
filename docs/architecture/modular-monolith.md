# Modular monolith rules

Each business module (`identity`, `organization`, `supplier`, `procurement`,
`approval`, `budget`, `purchaseorder`, `invoice`, `notification`, `audit`,
`analytics`, `ai`) materializes as:

```
<module>/
  domain/          entities, value objects, domain services, repository ports
  application/     use cases, input/output ports, DTOs
  infrastructure/  JPA adapters, external clients, config (frameworks live here only)
  api/             REST controllers, request/response mappers, OpenAPI annotations
```

Plus `shared/kernel` (events, base types) and `shared/multitenancy`
(tenant propagation). See ADR-001 for why not microservices.

## Dependency policy (enforced by ArchUnit in CI)

1. `api` may depend on `application` and `domain` only. Never on
   `infrastructure` (own or foreign).
2. `application` may depend on `domain` and `shared` only.
3. `domain` may depend on `shared.kernel` only. No Spring imports.
4. `infrastructure` may depend on anything inside its own module.
5. Cross-module: `application`/`api` may call another module's
   `application` port or subscribe to its domain events. Direct use of
   another module's `domain` entities or `infrastructure` is forbidden.
6. `shared` must not depend on any business module.
7. `analytics` is read-only: it never writes to other modules' state.
8. `notification` and `audit` consume events; nothing calls them synchronously.

## Conventions

- One aggregate root per transaction; money-moving code locks rows
  (`SELECT ... FOR UPDATE`) and is covered by a concurrency test.
- Repository interfaces (ports) live in `domain`; Spring Data
  implementations live in `infrastructure`.
- Controllers are thin: validate input, call one use case, map the result.
  Business rules never live in controllers.
- New modules start as a `package-info.java` reserving the namespace;
  layers appear with the first feature, never speculatively.
