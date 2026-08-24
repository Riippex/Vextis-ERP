variable "project_id" {
  description = "Google Cloud project that trusts GitHub Actions."
  type        = string
}

variable "cloud_build_service_account_email" {
  description = "Keyless build identity assumed by the trusted GitHub workflow."
  type        = string
}

variable "github_repository_id" {
  description = "Immutable GitHub repository ID allowed to federate."
  type        = string
}

variable "github_repository_owner_id" {
  description = "Immutable GitHub repository owner ID allowed to federate."
  type        = string
}

variable "deploy_branch" {
  description = "Only branch allowed to publish delivery images."
  type        = string
}
