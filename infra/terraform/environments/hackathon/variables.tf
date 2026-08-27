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
  default     = "firebase-auth-20260823-r2"
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

variable "live_model" {
  description = <<-EOT
    Vertex AI Gemini Live-capable model used for Ask Vextis voice sessions.
    Live requires a distinct model variant from `gemini_model` — verify the
    current Live-capable model id available in this project/region in Vertex
    AI before relying on this default.
  EOT
  type        = string
  default     = "gemini-live-2.5-flash-native-audio"
}

variable "memory_bank_agent_engine_id" {
  description = "Existing Vertex AI Agent Engine id used by Memory Bank; empty disables the feature."
  type        = string
  default     = ""
}
