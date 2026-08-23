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
