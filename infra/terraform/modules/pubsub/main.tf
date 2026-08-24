resource "google_pubsub_topic" "order_events" {
  project = var.project_id
  name    = var.topic_id
  labels  = var.labels
}

resource "google_pubsub_topic_iam_member" "enterprise_core_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.order_events.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${var.publisher_service_account_email}"
}

resource "google_pubsub_topic_iam_member" "additional_enterprise_core_publishers" {
  for_each = var.additional_publisher_service_account_emails

  project = var.project_id
  topic   = google_pubsub_topic.order_events.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${each.value}"
}

resource "google_pubsub_subscription" "agent_runtime" {
  project                    = var.project_id
  name                       = "order-events-agent-runtime"
  topic                      = google_pubsub_topic.order_events.id
  ack_deadline_seconds       = 300
  message_retention_duration = "604800s"
  retain_acked_messages      = false
  labels                     = var.labels

  push_config {
    push_endpoint = "${var.agent_runtime_url}/events/pubsub"

    oidc_token {
      service_account_email = var.push_service_account_email
      audience              = var.agent_runtime_url
    }
  }

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }

  expiration_policy {
    ttl = ""
  }

  lifecycle {
    prevent_destroy = true
  }
}
