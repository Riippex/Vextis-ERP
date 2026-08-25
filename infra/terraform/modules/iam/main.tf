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

resource "google_service_account" "enterprise_core_public" {
  project      = var.project_id
  account_id   = "vextis-core-public-${var.environment}"
  display_name = "Vextis Public Enterprise Core (${var.environment})"
  description  = "Runtime identity for the Firebase-authenticated public GraphQL API."

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

resource "google_service_account" "cloud_build" {
  project      = var.project_id
  account_id   = "vextis-build-${var.environment}"
  display_name = "Vextis Delivery (${var.environment})"
  description  = "Federated identity for selective Vextis builds and deployments."

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

resource "google_project_iam_member" "enterprise_core_public_cloud_sql" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.enterprise_core_public.email}"

  condition {
    title       = "vextis_hackathon_public_database_only"
    description = "Limit the public Enterprise Core runtime to its hackathon Cloud SQL instance."
    expression  = "resource.name == 'projects/${var.project_id}/instances/${var.cloud_sql_instance_name}' && resource.service == 'sqladmin.googleapis.com'"
  }
}

resource "google_project_iam_member" "agent_runtime_vertex_ai" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.agent_runtime.email}"
}

resource "google_project_iam_member" "cloud_build_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_project_iam_member" "cloud_build_submitter" {
  project = var.project_id
  role    = "roles/cloudbuild.builds.editor"
  member  = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_project_iam_member" "cloud_build_cloud_run_developer" {
  project = var.project_id
  role    = "roles/run.developer"
  member  = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_project_iam_member" "cloud_build_firebase_hosting_admin" {
  project = var.project_id
  role    = "roles/firebasehosting.admin"
  member  = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_project_iam_member" "cloud_build_service_usage_consumer" {
  project = var.project_id
  role    = "roles/serviceusage.serviceUsageConsumer"
  member  = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_service_account_iam_member" "cloud_build_can_use_itself" {
  service_account_id = google_service_account.cloud_build.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_service_account_iam_member" "cloud_build_can_use_enterprise_core" {
  service_account_id = google_service_account.enterprise_core.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_service_account_iam_member" "cloud_build_can_use_enterprise_core_public" {
  service_account_id = google_service_account.enterprise_core_public.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.cloud_build.email}"
}

resource "google_service_account_iam_member" "cloud_build_can_use_agent_runtime" {
  service_account_id = google_service_account.agent_runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.cloud_build.email}"
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

resource "google_secret_manager_secret_iam_member" "enterprise_core_public_database_password" {
  project   = var.project_id
  secret_id = var.database_password_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.enterprise_core_public.email}"
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

resource "google_service_account_iam_member" "enterprise_core_public_can_sign_upload_urls" {
  service_account_id = google_service_account.enterprise_core_public.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.enterprise_core_public.email}"
}
