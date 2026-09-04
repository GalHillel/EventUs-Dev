variable "app_image" {
  description = "Initial API image. Jenkins takes over the tag after the first apply."
  type        = string

  validation {
    condition     = can(regex("^[^:]+:[^:]+$", var.app_image)) && !endswith(var.app_image, ":latest")
    error_message = "app_image needs an explicit tag and must not be :latest, or rollout undo cannot swap images."
  }
}

variable "healer_image" {
  description = "Image for the self-healing job"
  type        = string
}

variable "mongo_password" {
  description = "MongoDB root password"
  type        = string
  sensitive   = true
}

variable "elastic_version" {
  description = "Tag shared by Elasticsearch, Kibana and Filebeat"
  type        = string
  default     = "8.19.20"
}
