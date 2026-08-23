variable "project_id" {
  description = "Google Cloud project for the hackathon environment."
  type        = string
  default     = "vextis-erp"
}

variable "region" {
  description = "Primary Google Cloud region."
  type        = string
  default     = "us-central1"
}

variable "enterprise_core_image_tag" {
  description = "Enterprise Core image tag promoted to the hackathon service."
  type        = string
  default     = "bootstrap-20260823-r3"
}

variable "agent_runtime_image_tag" {
  description = "Agent Runtime image tag promoted to the hackathon service."
  type        = string
  default     = "bootstrap-20260823-r4"
}

variable "gemini_model" {
  description = "Vertex AI Gemini model used by Agent Runtime."
  type        = string
  default     = "gemini-3.5-flash"
}
