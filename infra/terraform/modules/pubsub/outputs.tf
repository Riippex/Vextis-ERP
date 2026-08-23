output "topic_id" {
  description = "Pub/Sub topic identifier for business events."
  value       = google_pubsub_topic.order_events.name
}
