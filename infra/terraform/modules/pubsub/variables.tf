variable "project_id" {
  description = "Google Cloud project that owns the Pub/Sub topic."
  type        = string
}

variable "topic_id" {
  description = "Topic used by the Enterprise Core transactional outbox."
  type        = string
}

variable "publisher_service_account_email" {
  description = "Only service identity allowed to publish business events."
  type        = string
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
