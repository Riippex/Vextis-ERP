output "enterprise_core_url" {
  description = "Private Enterprise Core Cloud Run URL."
  value       = google_cloud_run_v2_service.enterprise_core.uri
}

output "agent_runtime_url" {
  description = "Private Agent Runtime Cloud Run URL."
  value       = google_cloud_run_v2_service.agent_runtime.uri
}
