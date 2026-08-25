locals {
  assets_bucket_name = "vextis-erp-hackathon-assets"
  labels = {
    application = "vextis"
    environment = "hackathon"
    managed_by  = "terraform"
    cost_center = "hackathon"
  }
}

resource "google_project_service" "iam_credentials" {
  project            = var.project_id
  service            = "iamcredentials.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "firebase_management" {
  project            = var.project_id
  service            = "firebase.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "firebase_hosting" {
  project            = var.project_id
  service            = "firebasehosting.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "identity_toolkit" {
  project            = var.project_id
  service            = "identitytoolkit.googleapis.com"
  disable_on_destroy = false
}

resource "google_identity_platform_config" "default" {
  project = var.project_id
  authorized_domains = [
    "localhost",
    "vextis-erp.firebaseapp.com",
    "vextis-erp.web.app",
  ]

  sign_in {
    allow_duplicate_emails = false

    anonymous {
      enabled = false
    }

    email {
      enabled           = true
      password_required = true
    }

    phone_number {
      enabled = false
    }
  }

  client {
    permissions {
      disabled_user_deletion = true
      disabled_user_signup   = true
    }
  }

  depends_on = [google_project_service.identity_toolkit]
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
  core_callback_secret_id     = "vextis-core-callback-token"
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
  core_callback_secret_id                      = module.iam.core_callback_secret_id
  pubsub_topic_id                              = "order-events"
  gemini_model                                 = var.gemini_model
  live_model                                   = var.live_model
  assets_bucket_name                           = local.assets_bucket_name
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

  project_id                                   = var.project_id
  region                                       = var.region
  assets_bucket_name                           = local.assets_bucket_name
  build_source_bucket_name                     = "vextis-erp-hackathon-build-source"
  enterprise_core_service_account_email        = module.iam.enterprise_core_email
  enterprise_core_public_service_account_email = module.iam.enterprise_core_public_email
  agent_runtime_service_account_email          = module.iam.agent_runtime_email
  cloud_build_service_account_email            = module.iam.cloud_build_email
  assets_cors_origins = [
    "http://localhost:4200",
    "https://vextis-erp.firebaseapp.com",
    "https://vextis-erp.web.app",
  ]
  purchase_order_retention_days = 30
  labels                        = local.labels
}

module "github_oidc" {
  source = "../../modules/github-oidc"

  project_id                        = var.project_id
  cloud_build_service_account_email = module.iam.cloud_build_email
  github_repository_id              = "1338929025"
  github_repository_owner_id        = "221794453"
  deploy_branch                     = "main"

  depends_on = [module.iam]
}
