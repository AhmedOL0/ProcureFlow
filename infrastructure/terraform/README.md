# Terraform: Hetzner Cloud production target (NOT APPLIED)

Concrete reference target for ADR-011: one CX22 in `nbg1`, attached 20 GB
xfs volume, default-deny firewall (443 world-open, 22 admin-only).
Rough cost ~EUR 5.50/mo (2026-10 list) plus egress/object storage.

Validated with `hashicorp/terraform:1.9` (`init -backend=false` +
`validate` green 2026-10-14). Never `terraform apply` from here without
a token and a partial-backend config:

```bash
docker run --rm -v "${PWD}:/w" -w /w hashicorp/terraform:1.9 init -backend=false -input=false
docker run --rm -v "${PWD}:/w" -w /w hashicorp/terraform:1.9 validate
# apply (needs TF_VAR_hcloud_token + backend.hcl, both uncommitted):
# terraform init -backend-config=backend.hcl && terraform apply
```

After apply: install Docker on the server, copy `docker-compose.prod.yml`
+ `.env` (real secrets, never committed), follow
`infrastructure/prod/README.md`.
