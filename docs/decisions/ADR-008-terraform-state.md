# ADR-008: Terraform state backend and provider floor

Date: 2026-10-09. Status: accepted (design only — no resources applied yet).

## Context

Phase 6 needs a deploy story: where Terraform state lives, which provider
floor we target, and how applies run. The repo carries a skeleton
(`infrastructure/terraform/`, `validate`-only) and the app is a Compose-run
monolith with Postgres/Redis/RabbitMQ.

## Decision

- Provider floor: AWS (S3 + RDS Postgres 16 + ElastiCache + MSK or RabbitMQ
  on ECS/EC2), region pinned per environment. Postgres 16 matches local
  and CI bit-for-bit; no Aurora-only features.
- State: S3 backend with DynamoDB locking, one state per environment
  (`env:/` workspaces, never a single shared state).
- Applies run only from CI on `main` (OIDC role, no static keys); plans
  comment on the PR. Local `terraform apply` against shared environments
  is forbidden.
- Secrets stay out of state inputs: DB passwords and `JWT_SECRET` come
  from the CI secret store into SSM Parameter Store (SecureString);
  Terraform reads them by ARN, never by value.

## Alternatives considered

- Terraform Cloud remote state: less to operate, but couples a portfolio
  project to a vendor account and hides the locking model we want to
  demonstrate.
- Local state committed to git: rejected — state holds secrets and locks
  nothing.

## Consequences

- `terraform init -backend-config` per environment; `validate` stays green
  in CI until real resources land (first: VPC + RDS + SSM wiring).
- The S3/DynamoDB bootstrap itself is a documented click-ops step (chicken
  and egg), then everything else is code.
