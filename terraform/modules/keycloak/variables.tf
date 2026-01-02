# variables.tf for keycloak module

variable "name" {
  description = "Name of the Keycloak container"
  type        = string
}

variable "network_name" {
  description = "Name of the Docker network to connect to"
  type        = string
}

variable "admin_user" {
  description = "Keycloak admin username"
  type        = string
}

variable "admin_password" {
  description = "Keycloak admin password"
  type        = string
  sensitive   = true
}

variable "http_port" {
  description = "External HTTP port for Keycloak"
  type        = number
}

variable "realm_file_path" {
  description = "Absolute path to the Keycloak realm JSON file"
  type        = string
}

variable "db_host" {
  description = "Hostname of the database"
  type        = string
}

variable "db_port" {
  description = "Port of the database"
  type        = number
  default     = 3306
}