# File: modules/mailhog/variables.tf

variable "name" {
  description = "Container name for MailHog"
  type        = string
}

variable "network_name" {
  description = "Docker network to attach MailHog to"
  type        = string
}

variable "smtp_port" {
  description = "External SMTP port to expose"
  type        = number
  default     = 1025
}

variable "ui_port" {
  description = "External web UI port to expose"
  type        = number
  default     = 8025
}
