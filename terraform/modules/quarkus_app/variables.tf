variable "name" {
  description = "Name of the Quarkus container"
  type        = string
}

variable "image" {
  description = "Docker image for the Quarkus application"
  type        = string
}

variable "internal_port" {
  description = "Internal HTTP port of the Quarkus application"
  type        = number
}

variable "external_port" {
  description = "External HTTP port to map to the host"
  type        = number
}

variable "https_external_port" {
  description = "External HTTPS port to map to the host"
  type        = number
  default     = null # Make it optional
}

variable "network_name" {
  description = "Name of the Docker network to connect to"
  type        = string
}

variable "db_host" {
  description = "Hostname of the database"
  type        = string
}

variable "db_port" {
  description = "Port of the database"
  type        = number
}

variable "mysql_ca_cert_path" {
  description = "Absolute path to the MySQL CA certificate on the host"
  type        = string
}

variable "env_vars" {
  description = "Map of environment variables to pass to the container"
  type        = map(string)
  default     = {}
}