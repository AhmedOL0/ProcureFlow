# ProcureFlow production target: Hetzner Cloud (EU), single VM + volume.
#
# Chosen 2026-10-14 as the deployment-ready reference (ADR-011): cheapest
# honest shape for a local-only product — one CX22, firewall default-deny
# with 443 open, data on an attached volume (snapshots + the WAL/dump
# procedure from infrastructure/prod). Managed Postgres (Neon/Supabase/RDS)
# stays the recommendation the moment shared tenant data exists; this target
# does NOT claim PITR by itself.
#
# NOT APPLIED HERE: no token exists in this repo, no `terraform apply` has
# ever run. Validate only (`terraform init -backend=false && validate`,
# e.g. via hashicorp/terraform:1.9 docker). Applying needs a token plus a
# partial S3-compatible backend config passed with -backend-config.
#
# Rough cost (2026-10, Hetzner list): CX22 ~EUR 4.5/mo + 20 GB volume
# ~EUR 1/mo. Egress/object storage for off-site dumps is extra.

variable "environment" {
  description = "Deployment environment name (staging, prod)."
  type        = string
  default     = "staging"
}

variable "project" {
  description = "Resource name prefix."
  type        = string
  default     = "procureflow"
}

variable "location" {
  description = "Hetzner location for server, volume and firewall."
  type        = string
  default     = "nbg1"
}

variable "server_type" {
  description = "Hetzner server plan (CX22: 2 vCPU, 4 GB RAM)."
  type        = string
  default     = "cx22"
}

variable "image" {
  description = "Server OS image."
  type        = string
  default     = "ubuntu-24.04"
}

variable "volume_size_gb" {
  description = "Attached data volume for pgdata + WAL + dumps."
  type        = number
  default     = 20
}

variable "ssh_key_name" {
  description = "Existing Hetzner SSH key name for admin access."
  type        = string
}

variable "admin_cidr" {
  description = "CIDR allowed SSH access (your IP, not 0.0.0.0/0)."
  type        = string
}

variable "hcloud_token" {
  description = "Hetzner Cloud API token. Never committed; TF_VAR_hcloud_token env only."
  type        = string
  sensitive   = true
}
