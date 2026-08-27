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
        name  = "VEXTIS_EXPOSURE"
        value = "INTERNAL"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = "-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
      }
      env {
        name  = "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"
        value = "4"
      }
      env {
        name  = "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE"
        value = "0"
      }
    }
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes = [
      client,
      client_version,
      template[0].containers[0].image,
    ]
  }
}

resource "google_cloud_run_v2_service" "enterprise_core_public" {
  project             = var.project_id
  location            = var.region
  name                = "vextis-enterprise-core-public"
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = true
  labels              = var.labels

  template {
    service_account                  = var.enterprise_core_public_service_account_email
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
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.project_id
      }
      env {
        name  = "VEXTIS_FIREBASE_PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "VEXTIS_EXPOSURE"
        value = "PUBLIC"
      }
      env {
        name  = "VEXTIS_DOCUMENTS_BUCKET"
        value = var.assets_bucket_name
      }
      env {
        name  = "VEXTIS_DOCUMENTS_SIGNING_SERVICE_ACCOUNT"
        value = var.enterprise_core_public_service_account_email
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
        name  = "VEXTIS_AGENT_RUNTIME_CHAT_URL"
        value = "${google_cloud_run_v2_service.agent_runtime.uri}/v1/chat/complete"
      }
      env {
        name = "VEXTIS_CORE_CALLBACK_TOKEN"
        value_source {
          secret_key_ref {
            secret  = var.core_callback_secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "VEXTIS_AGENT_RUNTIME_PUBLIC_WS_URL"
        value = replace(google_cloud_run_v2_service.agent_runtime.uri, "https://", "wss://")
      }
      env {
        name  = "GRAPHQL_GRAPHIQL_ENABLED"
        value = "false"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = "-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
      }
      env {
        name  = "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"
        value = "4"
      }
      env {
        name  = "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE"
        value = "0"
      }
    }
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes = [
      client,
      client_version,
      template[0].containers[0].image,
    ]
  }
}

resource "google_cloud_run_v2_service_iam_member" "public_invokes_enterprise_core_public" {
  project  = var.project_id
  location = google_cloud_run_v2_service.enterprise_core_public.location
  name     = google_cloud_run_v2_service.enterprise_core_public.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service" "agent_runtime" {
  project             = var.project_id
  location            = var.region
  name                = "vextis-agent-runtime"
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = true
  labels              = var.labels

  template {
    service_account = var.agent_runtime_service_account_email
    # 1800s (Cloud Run's max is 3600s), not the default 300s: a Live voice
    # session's WebSocket is one long-lived request, unlike the short
    # request/response tool calls this timeout used to only need to cover.
    timeout                          = "1800s"
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
        name  = "VEXTIS_CHAT_ENABLED"
        value = "true"
      }
      env {
        name  = "VEXTIS_MEMORY_BANK_ENABLED"
        value = var.memory_bank_agent_engine_id == "" ? "false" : "true"
      }
      env {
        name  = "VEXTIS_MEMORY_BANK_AGENT_ENGINE_ID"
        value = var.memory_bank_agent_engine_id
      }
      env {
        name = "VEXTIS_CORE_CALLBACK_TOKEN"
        value_source {
          secret_key_ref {
            secret  = var.core_callback_secret_id
            version = "latest"
          }
        }
      }
      env {
        # Not yet reachable by a browser: no allUsers invoker binding exists
        # on this service until the Phase 5 public-exposure change is
        # separately reviewed and applied. Mounting the route now only
        # allows already-IAM-authorized private callers to test it.
        name  = "VEXTIS_LIVE_ENABLED"
        value = "true"
      }
      env {
        name  = "VEXTIS_LIVE_MODEL"
        value = var.live_model
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
    ignore_changes = [
      client,
      client_version,
      template[0].containers[0].image,
    ]
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

# Private, service-to-service only: lets the public Enterprise Core call
# Agent Runtime's /v1/chat/complete for Ask Vextis text messages. This is
# unrelated to (and does not by itself enable) the public allUsers binding a
# browser's Live WebSocket would need — that is a separate, later change.
resource "google_cloud_run_v2_service_iam_member" "enterprise_core_public_invokes_agent_runtime" {
  project  = var.project_id
  location = google_cloud_run_v2_service.agent_runtime.location
  name     = google_cloud_run_v2_service.agent_runtime.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.enterprise_core_public_service_account_email}"
}
