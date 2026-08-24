output "instance_name" {
  description = "Cloud SQL instance name."
  value       = google_sql_database_instance.postgres.name
}

output "connection_name" {
  description = "Cloud SQL Connector/Auth Proxy connection name."
  value       = google_sql_database_instance.postgres.connection_name
}

output "database_name" {
  description = "Application database name."
  value       = google_sql_database.application.name
}

output "password_secret_id" {
  description = "Secret Manager identifier for the application database password."
  value       = google_secret_manager_secret.database_password.secret_id
}
