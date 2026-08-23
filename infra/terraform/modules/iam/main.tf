data "google_project" "current" {
  project_id = var.project_id
}

resource "google_service_account" "enterprise_core" {
  project      = var.project_id
  account_id   = "vextis-core-${var.environment}"
  display_name = "Vextis Enterprise Core (${var.environment})"
  description  = "Runtime identity for the Java Enterprise Core."

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account" "agent_runtime" {
  project      = var.project_id
  account_id   = "vextis-agent-${var.environment}"
  display_name = "Vextis Agent Runtime (${var.environment})"
  description  = "Runtime identity for the Python Agent Runtime."

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account" "pubsub_push" {
  project      = var.project_id
  account_id   = "vextis-push-${var.environment}"
  display_name = "Vextis Pub/Sub Push (${var.environment})"
  description  = "OIDC identity used only for authenticated Pub/Sub push delivery."

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_member" "enterprise_core_cloud_sql" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.enterprise_core.email}"

  condition {
    title       = "vextis_hackathon_database_only"
    description = "Limit Enterprise Core connectivity to its hackathon Cloud SQL instance."
    expression  = "resource.name == 'projects/${var.project_id}/instances/${var.cloud_sql_instance_name}' && resource.service == 'sqladmin.googleapis.com'"
  }
}

resource "google_project_iam_member" "agent_runtime_vertex_ai" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.agent_runtime.email}"
}

resource "google_secret_manager_secret" "agent_tools_token" {
  project             = var.project_id
  secret_id           = var.agent_tools_secret_id
  labels              = var.labels
  deletion_protection = true

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_iam_member" "enterprise_core_database_password" {
  project   = var.project_id
  secret_id = var.database_password_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.enterprise_core.email}"
}

resource "google_secret_manager_secret_iam_member" "enterprise_core_agent_tools_token" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.agent_tools_token.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.enterprise_core.email}"
}

resource "google_secret_manager_secret_iam_member" "agent_runtime_agent_tools_token" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.agent_tools_token.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.agent_runtime.email}"
}

resource "google_service_account_iam_member" "pubsub_can_sign_push_tokens" {
  service_account_id = google_service_account.pubsub_push.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}
