# Hetzner Cloud provider + partial S3 backend (filled at apply time).
terraform {
  required_version = ">= 1.9.0"
  required_providers {
    hcloud = {
      source  = "hetznercloud/hcloud"
      version = "~> 1.52"
    }
  }
  # Partial backend: `terraform init -backend-config=backend.hcl` with
  # bucket/key/region/endpoints once state storage exists. Nothing committed.
  backend "s3" {}
}
