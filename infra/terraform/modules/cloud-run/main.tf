locals {
  database_url = "jdbc:postgresql:///${var.database_name}?cloudSqlInstance=${var.cloud_sql_connection_name}&socketFactory=com.google.cloud.sql.postgres.SocketFactory&ipTypes=PUBLIC"
}

resource "google_cloud_run_v2_service" "enterprise_core" {
  project             = var.project_id
  location            = var.region
  name                = "vextis-enterprise-core"
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = true
  labels              = var.labels

  template {
    service_account                  = var.enterprise_core_service_account_email
    timeout                          = "60s"
    max_instance_request_concurrency = 40

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = var.enterprise_core_image

      ports {
        name           = "http1"
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      env {
        name  = "DATABASE_URL"
        value = local.database_url
      }
      env {
        name  = "POSTGRES_USER"
        value = "vextis_app"
      }
      env {
        name = "POSTGRES_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.database_password_secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "VEXTIS_AGENT_TOOLS_TOKEN"
        value_source {
          secret_key_ref {
            secret  = var.agent_tools_secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.project_id
      }
      env {
        name  = "VEXTIS_PUBSUB_ENABLED"
        value = "true"
      }
      env {
        name  = "VEXTIS_PUBSUB_TOPIC_ID"
        value = var.pubsub_topic_id
      }
      env {
        name  = "GRAPHQL_GRAPHIQL_ENABLED"
        value = "false"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = "-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
      }
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_cloud_run_v2_service" "agent_runtime" {
  project             = var.project_id
  location            = var.region
  name                = "vextis-agent-runtime"
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = true
  labels              = var.labels

  template {
    service_account                  = var.agent_runtime_service_account_email
    timeout                          = "300s"
    max_instance_request_concurrency = 20

    scaling {
      min_instance_count = 0
      max_instance_count = 1
    }

    containers {
      image = var.agent_runtime_image

      ports {
        name           = "http1"
        container_port = 8081
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      env {
        name  = "VEXTIS_ENVIRONMENT"
        value = "hackathon"
      }
      env {
        name  = "VEXTIS_ENTERPRISE_CORE_URL"
        value = google_cloud_run_v2_service.enterprise_core.uri
      }
      env {
        name  = "VEXTIS_ENTERPRISE_CORE_AUDIENCE"
        value = google_cloud_run_v2_service.enterprise_core.uri
      }
      env {
        name = "VEXTIS_AGENT_TOOLS_TOKEN"
        value_source {
          secret_key_ref {
            secret  = var.agent_tools_secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "VEXTIS_PUBSUB_PUSH_ENABLED"
        value = "true"
      }
      env {
        name  = "VEXTIS_GEMINI_MODEL"
        value = var.gemini_model
      }
      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.project_id
      }
      env {
        name  = "GOOGLE_CLOUD_LOCATION"
        value = var.region
      }
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_cloud_run_v2_service_iam_member" "agent_invokes_enterprise_core" {
  project  = var.project_id
  location = google_cloud_run_v2_service.enterprise_core.location
  name     = google_cloud_run_v2_service.enterprise_core.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.agent_runtime_service_account_email}"
}

resource "google_cloud_run_v2_service_iam_member" "pubsub_invokes_agent_runtime" {
  project  = var.project_id
  location = google_cloud_run_v2_service.agent_runtime.location
  name     = google_cloud_run_v2_service.agent_runtime.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.pubsub_push_service_account_email}"
}
