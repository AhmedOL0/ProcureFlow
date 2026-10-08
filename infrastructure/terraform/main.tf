# Entry point for the future cloud deployment. Intentionally empty apart
# from locals: resources arrive as modules in the DevOps epic, after the
# provider and state backend decisions are recorded in a new ADR.

locals {
  name_prefix = "${var.project}-${var.environment}"
}
