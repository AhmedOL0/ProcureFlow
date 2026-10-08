# Local Docker usage: `docker compose up -d postgres redis rabbitmq` for
# development dependencies, or `docker compose up -d --build` for the full
# stack once backend/frontend images are built. Required secrets come from
# `.env` (see root `.env.example`); compose fails fast if they are missing.
# Service catalog: postgres:5432, redis:6379, rabbitmq:5672 (+15672 mgmt),
# backend:8080, frontend:4200 (nginx on :80 in-container).
