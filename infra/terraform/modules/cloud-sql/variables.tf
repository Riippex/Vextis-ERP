variable "project_id" {
  description = "Google Cloud project that owns the database resources."
  type        = string
}

variable "region" {
  description = "Google Cloud region for the Cloud SQL instance."
  type        = string
}

variable "instance_name" {
  description = "Cloud SQL instance name."
  type        = string
}

variable "database_name" {
  description = "Application database name."
  type        = string
}

variable "password_secret_id" {
  description = "Secret Manager secret that will hold the application database password."
  type        = string
}

variable "labels" {
  description = "Labels applied to cost-bearing and security-sensitive resources."
  type        = map(string)
}
