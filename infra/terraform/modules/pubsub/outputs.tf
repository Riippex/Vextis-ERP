output "topic_id" {
  description = "Pub/Sub topic identifier for business events."
  value       = google_pubsub_topic.order_events.name
}

output "agent_runtime_subscription_id" {
  description = "Authenticated push subscription identifier."
  value       = google_pubsub_subscription.agent_runtime.name
}
