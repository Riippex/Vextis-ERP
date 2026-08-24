output "enterprise_core_url" {
  description = "Private Enterprise Core Cloud Run URL."
  value       = google_cloud_run_v2_service.enterprise_core.uri
}

output "enterprise_core_public_url" {
  description = "Public Cloud Run URL protected by Firebase Authentication in the application."
  value       = google_cloud_run_v2_service.enterprise_core_public.uri
}

output "agent_runtime_url" {
  description = "Private Agent Runtime Cloud Run URL."
  value       = google_cloud_run_v2_service.agent_runtime.uri
}
