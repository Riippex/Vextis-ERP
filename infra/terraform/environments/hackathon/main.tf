locals {
  labels = {
    application = "vextis"
    environment = "hackathon"
    managed_by  = "terraform"
    cost_center = "hackathon"
  }
}

module "cloud_sql" {
  source = "../../modules/cloud-sql"

  project_id         = var.project_id
  region             = var.region
  instance_name      = "vextis-hackathon-pg"
  database_name      = "vextis"
  password_secret_id = "vextis-db-password"
  labels             = local.labels
}

module "iam" {
  source = "../../modules/iam"

  project_id                  = var.project_id
  environment                 = "hackathon"
  cloud_sql_instance_name     = module.cloud_sql.instance_name
  database_password_secret_id = module.cloud_sql.password_secret_id
  agent_tools_secret_id       = "vextis-agent-tools-token"
  labels                      = local.labels
}

module "pubsub" {
  source = "../../modules/pubsub"

  project_id                      = var.project_id
  topic_id                        = "order-events"
  publisher_service_account_email = module.iam.enterprise_core_email
  labels                          = local.labels
}
