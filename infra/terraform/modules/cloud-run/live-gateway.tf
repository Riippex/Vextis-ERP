# Public Live voice gateway.
#
# This is the same Agent Runtime image deployed a second time with only the Live
# WebSocket route mounted, mirroring how vextis-enterprise-core-public separates
# the browser-facing surface from the private one. It is the only Agent Runtime
# service carrying an allUsers invoker binding: Pub/Sub push, the internal chat
# endpoint and every /internal/agent-tools call stay on the private service and
# remain unreachable from the internet.
#
# Cloud Run IAM cannot authenticate a browser WebSocket, so the authorization
# boundary is the ephemeral session token Enterprise Core issues, presented in
# the first frame and validated against Core before any bridge is created. The
# runtime bounds what an unauthenticated caller can consume on an instance
# through its auth timeout, frame-size caps and session expiry deadline.
#
# Deliberately left without prevent_destroy/deletion_protection: this is a
# public surface, and being able to withdraw it in a single apply is worth more
# than guarding a stateless service against accidental deletion.
resource "google_cloud_run_v2_service" "agent_runtime_live" {
  project             = var.project_id
  location            = var.region
  name                = "vextis-agent-runtime-live"
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false
  labels              = var.labels

  template {
    service_account = var.agent_runtime_live_service_account_email
    # Comfortably above live_max_session_seconds so the application deadline,
    # not Cloud Run, is what ends a session.
    timeout = "${var.live_max_session_seconds + 60}s"
    # A voice session holds an instance for its whole duration, so concurrency
    # here caps simultaneous callers per instance rather than throughput.
    max_instance_request_concurrency = 4

    scaling {
      min_instance_count = 0
      max_instance_count = 2
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
        # Audio streams continuously between requests-in-flight boundaries, so
        # the CPU has to stay allocated for the life of the connection.
        cpu_idle          = false
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
        name  = "VEXTIS_LIVE_ENABLED"
        value = "true"
      }
      # Everything else this image can serve stays off, so the public surface is
      # exactly one route: the Live WebSocket.
      env {
        name  = "VEXTIS_PUBSUB_PUSH_ENABLED"
        value = "false"
      }
      env {
        name  = "VEXTIS_CHAT_ENABLED"
        value = "false"
      }
      env {
        name  = "VEXTIS_MEMORY_BANK_ENABLED"
        value = "false"
      }
      env {
        name  = "VEXTIS_LIVE_MODEL"
        value = var.live_model
      }
      env {
        name  = "VEXTIS_LIVE_LOCATION"
        value = var.region
      }
      env {
        name  = "VEXTIS_LIVE_MAX_SESSION_SECONDS"
        value = tostring(var.live_max_session_seconds)
      }
      env {
        name  = "VEXTIS_LIVE_AUTH_TIMEOUT_SECONDS"
        value = "5"
      }
      env {
        name  = "VEXTIS_LIVE_MAX_AUDIO_FRAME_BYTES"
        value = "65536"
      }
      env {
        name  = "VEXTIS_LIVE_MAX_TEXT_FRAME_BYTES"
        value = "4096"
      }
      env {
        name  = "VEXTIS_GEMINI_MODEL"
        value = var.gemini_model
      }
      env {
        name  = "VEXTIS_GEMINI_LOCATION"
        value = "us"
      }
      env {
        name  = "GOOGLE_GENAI_USE_VERTEXAI"
        value = "true"
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
    ignore_changes = [
      client,
      client_version,
      template[0].containers[0].image,
    ]
  }
}

# A browser cannot present a Cloud Run identity token, so invocation is open and
# the ephemeral Live session token is the real authorization boundary.
resource "google_cloud_run_v2_service_iam_member" "public_invokes_agent_runtime_live" {
  project  = var.project_id
  location = google_cloud_run_v2_service.agent_runtime_live.location
  name     = google_cloud_run_v2_service.agent_runtime_live.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service_iam_member" "agent_runtime_live_invokes_enterprise_core" {
  project  = var.project_id
  location = google_cloud_run_v2_service.enterprise_core.location
  name     = google_cloud_run_v2_service.enterprise_core.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.agent_runtime_live_service_account_email}"
}
