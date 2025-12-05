variable "db_user" {
  description = "Database username"
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

variable "redis_password" {
  description = "Redis password"
  type        = string
  sensitive   = true
}

variable "app_image" {
  description = "Docker image for the Quarkus application"
  type        = string
  default     = "quarkus-admin:prod"
}
