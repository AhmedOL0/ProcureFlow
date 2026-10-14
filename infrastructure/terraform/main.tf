# ProcureFlow on Hetzner Cloud: one app server, one data volume, one
# default-deny firewall. Docker + the prod overlay land out of band
# (see infrastructure/prod/README.md); Terraform owns the rented shape.

locals {
  name_prefix = "${var.project}-${var.environment}"
}

resource "hcloud_firewall" "edge" {
  name = "${local.name_prefix}-edge"

  rule {
    description = "Public HTTPS to the TLS edge"
    direction   = "in"
    protocol    = "tcp"
    port        = "443"
    source_ips  = ["0.0.0.0/0", "::/0"]
  }

  rule {
    description = "Admin SSH only"
    direction   = "in"
    protocol    = "tcp"
    port        = "22"
    source_ips  = [var.admin_cidr]
  }
}

resource "hcloud_server" "app" {
  name        = "${local.name_prefix}-app"
  image       = var.image
  server_type = var.server_type
  location    = var.location
  ssh_keys    = [var.ssh_key_name]
  firewall_ids = [hcloud_firewall.edge.id]

  labels = {
    project     = var.project
    environment = var.environment
  }
}

resource "hcloud_volume" "data" {
  name     = "${local.name_prefix}-data"
  size     = var.volume_size_gb
  location = var.location
  format   = "xfs"

  labels = {
    project     = var.project
    environment = var.environment
  }
}

resource "hcloud_volume_attachment" "data" {
  volume_id = hcloud_volume.data.id
  server_id = hcloud_server.app.id
  automount = true
}

output "server_ipv4" {
  description = "Public IPv4 of the app server (point DNS + ACME here)."
  value       = hcloud_server.app.ipv4_address
}

output "volume_id" {
  description = "Data volume id (pgdata + WAL + dumps live here)."
  value       = hcloud_volume.data.id
}
