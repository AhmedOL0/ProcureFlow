# Terraform skeleton

Holds the future cloud deployment shape: `versions.tf` (toolchain + state
intent), `variables.tf` (non-secret inputs), `main.tf` (module entry point).
Nothing here creates infrastructure yet; applying an empty plan is the only
supported operation until the DevOps epic records the provider decision in
a new ADR and adds real modules. Validate syntax with `terraform validate`
after `terraform init -backend=false`.
