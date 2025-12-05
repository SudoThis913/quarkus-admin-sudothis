variable "name" {
  description = "Name of the Quarkus container"
  type        = string
}

variable "image" {
  description = "Docker image for the Quarkus app"
  type        = string
}

variable "internal_port" {
  description = "Internal port of the Quarkus app"
  type        = number
}

variable "external_port" {
  description = "External port mapped to the host"
  type        = number
}

variable "network_name" {
  description = "Name of the Docker network"
  type        = string
}

variable "env_vars" {
  description = "Environment variables for the container"
  type        = map(string)
  default     = {}
}

variable "db_host" {
  description = "Hostname for the database"
  type        = string
}

variable "db_port" {
  description = "Port for the database"
  type        = number
}