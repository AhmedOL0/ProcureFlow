# ADR-002: Angular + Spring Boot architecture

- Status: Accepted (Phase 1)
- Context: Need a serious enterprise frontend career signal (Angular) and a
  robust JVM backend for transactional procurement logic.
- Decision: Angular 21 SPA (Signals, RxJS, Reactive Forms, lazy features)
  talking to a Spring Boot 3.5 REST API (Java 21). Strict client/server
  split; no server-rendered pages, no business logic in the browser.
- Consequences: Two build pipelines and generated API types to keep in
  sync (OpenAPI codegen from Phase 3). Hiring-relevant stack on both sides.
