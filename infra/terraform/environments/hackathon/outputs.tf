output "cloud_sql_instance_name" {
  value = module.cloud_sql.instance_name
}

output "cloud_sql_connection_name" {
  value = module.cloud_sql.connection_name
}

output "database_name" {
  value = module.cloud_sql.database_name
}

output "database_password_secret_id" {
  value = module.cloud_sql.password_secret_id
}

output "enterprise_core_service_account" {
  value = module.iam.enterprise_core_email
}

output "agent_runtime_service_account" {
  value = module.iam.agent_runtime_email
}

output "pubsub_push_service_account" {
  value = module.iam.pubsub_push_email
}

output "agent_tools_secret_id" {
  value = module.iam.agent_tools_secret_id
}

output "order_events_topic_id" {
  value = module.pubsub.topic_id
}
