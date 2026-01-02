# variables.tf for mysql module

variable "name" {
  description = "Name of the MySQL container"
  type        = string
}

variable "username" {
  description = "MySQL username for the primary database"
  type        = string
}

variable "password" {
  description = "MySQL password for the primary database"
  type        = string
  sensitive   = true
}

variable "database" {
  description = "Name of the primary database to create"
  type        = string
}

variable "port" {
  description = "External port for MySQL"
  type        = number
}

variable "network_name" {
  description = "Name of the Docker network to connect to"
  type        = string
}

variable "additional_databases" {
  description = "A list of additional database names to create"
  type        = list(string)
  default     = []
}