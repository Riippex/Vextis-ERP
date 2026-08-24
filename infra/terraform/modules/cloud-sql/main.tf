resource "google_sql_database_instance" "postgres" {
  project             = var.project_id
  name                = var.instance_name
  region              = var.region
  database_version    = "POSTGRES_17"
  deletion_protection = true

  settings {
    tier                        = "db-f1-micro"
    edition                     = "ENTERPRISE"
    availability_type           = "ZONAL"
    activation_policy           = "ALWAYS"
    disk_type                   = "PD_SSD"
    disk_size                   = 10
    disk_autoresize             = false
    deletion_protection_enabled = true
    user_labels                 = var.labels

    backup_configuration {
      enabled                        = true
      start_time                     = "06:00"
      point_in_time_recovery_enabled = false

      backup_retention_settings {
        retained_backups = 3
        retention_unit   = "COUNT"
      }
    }

    ip_configuration {
      ipv4_enabled = true
      ssl_mode     = "ENCRYPTED_ONLY"
    }

    maintenance_window {
      day          = 7
      hour         = 7
      update_track = "stable"
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_sql_database" "application" {
  project  = var.project_id
  name     = var.database_name
  instance = google_sql_database_instance.postgres.name
}

resource "google_secret_manager_secret" "database_password" {
  project             = var.project_id
  secret_id           = var.password_secret_id
  labels              = var.labels
  deletion_protection = true

  replication {
    auto {}
  }
}
