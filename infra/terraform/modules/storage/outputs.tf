output "assets_bucket_name" {
  description = "Private Vextis application assets bucket."
  value       = google_storage_bucket.assets.name
}

output "build_source_bucket_name" {
  description = "Short-lived Cloud Build source bucket."
  value       = google_storage_bucket.build_source.name
}
