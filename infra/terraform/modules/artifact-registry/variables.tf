variable "project_id" {
  description = "Google Cloud project that owns the repository."
  type        = string
}

variable "region" {
  description = "Region colocated with Cloud Run."
  type        = string
}

variable "repository_id" {
  description = "Docker repository identifier."
  type        = string
}

variable "cloud_build_service_account_email" {
  description = "Build identity allowed to push container images."
  type        = string
}

variable "labels" {
  description = "Labels applied to the repository."
  type        = map(string)
}
