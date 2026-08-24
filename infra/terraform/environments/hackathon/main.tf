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

module "artifact_registry" {
  source = "../../modules/artifact-registry"

  project_id                        = var.project_id
  region                            = var.region
  repository_id                     = "vextis"
  cloud_build_service_account_email = module.iam.cloud_build_email
  labels                            = local.labels
}

module "cloud_run" {
  source = "../../modules/cloud-run"

  project_id                                   = var.project_id
  region                                       = var.region
  enterprise_core_image                        = "${module.artifact_registry.repository_url}/enterprise-core:${var.enterprise_core_image_tag}"
  agent_runtime_image                          = "${module.artifact_registry.repository_url}/agent-runtime:${var.agent_runtime_image_tag}"
  enterprise_core_service_account_email        = module.iam.enterprise_core_email
  enterprise_core_public_service_account_email = module.iam.enterprise_core_public_email
  agent_runtime_service_account_email          = module.iam.agent_runtime_email
  pubsub_push_service_account_email            = module.iam.pubsub_push_email
  cloud_sql_connection_name                    = module.cloud_sql.connection_name
  database_name                                = module.cloud_sql.database_name
  database_password_secret_id                  = module.cloud_sql.password_secret_id
  agent_tools_secret_id                        = module.iam.agent_tools_secret_id
  pubsub_topic_id                              = "order-events"
  gemini_model                                 = var.gemini_model
  labels                                       = local.labels

  depends_on = [module.iam]
}

module "pubsub" {
  source = "../../modules/pubsub"

  project_id                      = var.project_id
  topic_id                        = "order-events"
  publisher_service_account_email = module.iam.enterprise_core_email
  additional_publisher_service_account_emails = [
    module.iam.enterprise_core_public_email,
  ]
  agent_runtime_url          = module.cloud_run.agent_runtime_url
  push_service_account_email = module.iam.pubsub_push_email
  labels                     = local.labels
}

module "storage" {
  source = "../../modules/storage"

  project_id                            = var.project_id
  region                                = var.region
  assets_bucket_name                    = "vextis-erp-hackathon-assets"
  build_source_bucket_name              = "vextis-erp-hackathon-build-source"
  enterprise_core_service_account_email = module.iam.enterprise_core_email
  agent_runtime_service_account_email   = module.iam.agent_runtime_email
  cloud_build_service_account_email     = module.iam.cloud_build_email
  labels                                = local.labels
}

module "github_oidc" {
  source = "../../modules/github-oidc"

  project_id                        = var.project_id
  cloud_build_service_account_email = module.iam.cloud_build_email
  github_repository_id              = "1338929025"
  github_repository_owner_id        = "221794453"
  deploy_branch                     = "develop"

  depends_on = [module.iam]
}
