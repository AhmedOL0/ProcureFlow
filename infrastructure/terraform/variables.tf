# Input variables for the future cloud deployment. No defaults for secrets:
# secrets never travel through tfvars (see docs/security/secrets.md).

variable "environment" {
  description = "Deployment environment name (e.g. staging, prod)."
  type        = string
  default     = "staging"
}

variable "region" {
  description = "Cloud region for all resources."
  type        = string
  default     = "eu-west-1"
}

variable "project" {
  description = "Resource name prefix."
  type        = string
  default     = "procureflow"
}
