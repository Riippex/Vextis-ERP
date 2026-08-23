output "enterprise_core_email" {
  description = "Service identity for Enterprise Core."
  value       = google_service_account.enterprise_core.email
}

output "agent_runtime_email" {
  description = "Service identity for Agent Runtime."
  value       = google_service_account.agent_runtime.email
}

output "pubsub_push_email" {
  description = "OIDC identity for authenticated Pub/Sub push delivery."
  value       = google_service_account.pubsub_push.email
}

output "agent_tools_secret_id" {
  description = "Secret Manager identifier for the internal Agent Tools token."
  value       = google_secret_manager_secret.agent_tools_token.secret_id
}
