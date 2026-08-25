resource "google_pubsub_topic" "order_events" {
  project = var.project_id
  name    = var.topic_id
  labels  = var.labels
}

data "google_project" "current" {
  project_id = var.project_id
}

resource "google_pubsub_topic" "agent_runtime_dead_letter" {
  project = var.project_id
  name    = "${var.topic_id}-agent-runtime-dead-letter"
  labels  = var.labels
}

resource "google_pubsub_topic_iam_member" "pubsub_service_agent_dead_letter_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.agent_runtime_dead_letter.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}

resource "google_pubsub_subscription" "agent_runtime_dead_letter" {
  project                    = var.project_id
  name                       = "order-events-agent-runtime-dead-letter"
  topic                      = google_pubsub_topic.agent_runtime_dead_letter.id
  ack_deadline_seconds       = 60
  message_retention_duration = "604800s"
  retain_acked_messages      = false
  labels                     = var.labels

  expiration_policy {
    ttl = ""
  }

  lifecycle {
    prevent_destroy = true
  }
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

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.agent_runtime_dead_letter.id
    max_delivery_attempts = 5
  }

  expiration_policy {
    ttl = ""
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_pubsub_topic_iam_member.pubsub_service_agent_dead_letter_publisher]
}

resource "google_pubsub_subscription_iam_member" "pubsub_service_agent_source_subscriber" {
  project      = var.project_id
  subscription = google_pubsub_subscription.agent_runtime.name
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}
