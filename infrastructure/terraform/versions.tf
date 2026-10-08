# ProcureFlow cloud target (skeleton — nothing is applied yet).
# Phase 1 intent: document the shape so application code stays portable.
# Provider choice (AWS/GCP) and state backend are decided in the DevOps epic;
# until then every resource below is commented out on purpose.

terraform {
  required_version = ">= 1.9.0"
  # backend "s3" {}  # remote state lands here once the account exists
}

# Target shape (all resources arrive as modules in the DevOps epic):
# - network: VPC, private subnets for data, public subnets for the edge
# - data: managed PostgreSQL, managed Redis, managed RabbitMQ (or equivalent)
# - compute: container service for backend + frontend images built by CI
# - secrets: cloud secret manager wired to container env (never tfvars)
# - observability: managed Prometheus/Grafana or cloud-native equivalents
