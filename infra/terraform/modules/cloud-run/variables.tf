variable "project_id" {
  description = "Google Cloud project that owns the Cloud Run services."
  type        = string
}

variable "region" {
  description = "Region where the Cloud Run services execute."
  type        = string
}

variable "enterprise_core_image" {
  description = "Immutable Enterprise Core container image reference."
  type        = string
}

variable "agent_runtime_image" {
  description = "Immutable Agent Runtime container image reference."
  type        = string
}

variable "enterprise_core_service_account_email" {
  description = "Runtime identity for Enterprise Core."
  type        = string
}

variable "enterprise_core_public_service_account_email" {
  description = "Runtime identity for the public Firebase-authenticated Enterprise Core."
  type        = string
}

variable "agent_runtime_service_account_email" {
  description = "Runtime identity for Agent Runtime."
  type        = string
}

variable "pubsub_push_service_account_email" {
  description = "OIDC identity used by authenticated Pub/Sub push delivery."
  type        = string
}

variable "cloud_sql_connection_name" {
  description = "Cloud SQL connection name consumed by the Java connector."
  type        = string
}

variable "database_name" {
  description = "PostgreSQL database owned by Enterprise Core."
  type        = string
}

variable "database_password_secret_id" {
  description = "Secret Manager identifier for the database password."
  type        = string
}

variable "agent_tools_secret_id" {
  description = "Secret Manager identifier for internal business authorization."
  type        = string
}

variable "pubsub_topic_id" {
  description = "Transactional outbox destination topic."
  type        = string
}

variable "gemini_model" {
  description = "Vertex AI Gemini model used for planning."
  type        = string
}

variable "labels" {
  description = "Labels applied to Cloud Run services."
  type        = map(string)
}
