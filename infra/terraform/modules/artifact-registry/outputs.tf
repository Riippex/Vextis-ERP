output "repository_id" {
  description = "Artifact Registry repository identifier."
  value       = google_artifact_registry_repository.containers.repository_id
}

output "repository_url" {
  description = "Regional Docker repository URL."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.containers.repository_id}"
}
