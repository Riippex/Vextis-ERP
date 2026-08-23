variable "project_id" {
  description = "Google Cloud project that owns the buckets."
  type        = string
}

variable "region" {
  description = "Region colocated with Vextis runtimes."
  type        = string
}

variable "assets_bucket_name" {
  description = "Private bucket for source documents and generated assets."
  type        = string
}

variable "build_source_bucket_name" {
  description = "Private short-lived bucket for Cloud Build source archives."
  type        = string
}

variable "enterprise_core_service_account_email" {
  description = "Enterprise Core identity allowed to manage application objects."
  type        = string
}

variable "agent_runtime_service_account_email" {
  description = "Agent Runtime identity allowed to manage application objects."
  type        = string
}

variable "cloud_build_service_account_email" {
  description = "Cloud Build identity allowed to read staged source archives."
  type        = string
}

variable "labels" {
  description = "Labels applied to storage buckets."
  type        = map(string)
}
