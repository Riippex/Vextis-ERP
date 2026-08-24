variable "project_id" {
  description = "Google Cloud project that owns the Pub/Sub topic."
  type        = string
}

variable "topic_id" {
  description = "Topic used by the Enterprise Core transactional outbox."
  type        = string
}

variable "publisher_service_account_email" {
  description = "Primary Enterprise Core service identity allowed to publish business events."
  type        = string
}

variable "additional_publisher_service_account_emails" {
  description = "Additional Enterprise Core service identities allowed to publish business events."
  type        = set(string)
  default     = []
}

variable "agent_runtime_url" {
  description = "Private Agent Runtime URL that receives authenticated push events."
  type        = string
}

variable "push_service_account_email" {
  description = "Service identity used to mint Pub/Sub push OIDC tokens."
  type        = string
}

variable "labels" {
  description = "Labels applied to Pub/Sub resources."
  type        = map(string)
}
