variable "project_id" {
  description = "Google Cloud project that owns the service identities."
  type        = string
}

variable "environment" {
  description = "Environment suffix used in service-account identifiers."
  type        = string
}

variable "cloud_sql_instance_name" {
  description = "The only Cloud SQL instance Enterprise Core may connect to."
  type        = string
}

variable "database_password_secret_id" {
  description = "Secret containing the Enterprise Core database password."
  type        = string
}

variable "agent_tools_secret_id" {
  description = "Secret shared by Agent Runtime and Enterprise Core for internal tool authentication."
  type        = string
}

variable "labels" {
  description = "Labels applied to IAM-adjacent resources."
  type        = map(string)
}
