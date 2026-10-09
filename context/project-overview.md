# ProcureFlow — Project Overview

## What it is

AI-powered multi-tenant B2B procurement and supplier management SaaS.
One deployment serves many tenant companies; tenant data never crosses
tenant boundaries.

## Product definition

Buying organizations run procure-to-pay in one place: suppliers, purchase
requests, approval workflows, budgets, purchase orders, invoices — plus an
AI copilot that explains spend and drafts requests.

## Goals

- Demonstrate full-stack engineering hirable by a professional B2B product
  company: Angular enterprise frontend, Java 21 + Spring Boot backend,
  clean modular architecture, real testing, CI/CD, observability.
- Every increment is demoable, tested and documented. No fake
  production-ready functionality, ever.

## Target users

Requesters, approvers/managers, procurement officers, finance, tenant admins.
(Full personas in `README.md`.)

## Features by phase

- Phase 1 (done): repository, module boundaries, V1 schema, AI port, CI/docs.
- Phase 2 (done): JWT auth + refresh rotation, RBAC, tenants/departments/
  memberships, integration tests.
- Phase 3: suppliers, purchase requests (idempotent), purchase orders lite,
  OpenAPI type generation for Angular.
- Phase 4: approvals (delegation/escalation), budget reservation,
  event-driven notifications.
- Phase 5: full orders, invoices, payment status, audit trail.
- Phase 6: analytics KPIs, Groq adapter, copilot, extraction, quotation intel.

## Scope boundaries

- In scope: web SPA + REST API, one modular monolith, Postgres/Redis/
  RabbitMQ via compose, Groq behind the `ai` module only.
- Out of scope: mobile app, OAuth/social login, per-tenant schemas or
  encryption, microservices, cloud deployment (designed, not built).

## Source of truth

This folder is the session entry point: `project-overview.md` (what),
`architecture.md` (how it fits), `code-standards.md` + `ui-context.md`
(rules), `ai-workflow-rules.md` (process), `progress-tracker.md` (state).
Long-form design lives in `docs/`; if they disagree, the context file wins
and the doc gets updated in the same change.
