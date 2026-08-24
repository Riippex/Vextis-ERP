resource "google_storage_bucket" "assets" {
  project                     = var.project_id
  name                        = var.assets_bucket_name
  location                    = var.region
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
  labels                      = var.labels

  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  lifecycle_rule {
    condition {
      age            = 2
      matches_prefix = ["temp/"]
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age            = 14
      matches_prefix = ["generated/"]
    }
    action {
      type = "Delete"
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_storage_bucket" "build_source" {
  project                     = var.project_id
  name                        = var.build_source_bucket_name
  location                    = var.region
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
  labels                      = var.labels

  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  lifecycle_rule {
    condition {
      age = 1
    }
    action {
      type = "Delete"
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_storage_bucket_iam_member" "enterprise_core_object_user" {
  bucket = google_storage_bucket.assets.name
  role   = "roles/storage.objectUser"
  member = "serviceAccount:${var.enterprise_core_service_account_email}"
}

resource "google_storage_bucket_iam_member" "agent_runtime_object_user" {
  bucket = google_storage_bucket.assets.name
  role   = "roles/storage.objectUser"
  member = "serviceAccount:${var.agent_runtime_service_account_email}"
}

resource "google_storage_bucket_iam_member" "cloud_build_source_viewer" {
  bucket = google_storage_bucket.build_source.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${var.cloud_build_service_account_email}"
}

resource "google_storage_bucket_iam_member" "cloud_build_source_bucket_reader" {
  bucket = google_storage_bucket.build_source.name
  role   = "roles/storage.legacyBucketReader"
  member = "serviceAccount:${var.cloud_build_service_account_email}"
}

resource "google_storage_bucket_iam_member" "cloud_build_source_creator" {
  bucket = google_storage_bucket.build_source.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${var.cloud_build_service_account_email}"
}
