output "workload_identity_provider_name" {
  description = "Full provider resource name consumed by google-github-actions/auth."
  value       = google_iam_workload_identity_pool_provider.github.name
}
