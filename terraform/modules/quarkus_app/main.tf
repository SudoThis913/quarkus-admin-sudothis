terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

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


resource "docker_container" "quarkus" {
  name  = var.name
  image = var.image

  ports {
    internal = var.internal_port
    external = var.external_port
  }

  env = concat([for k, v in var.env_vars : "${k}=${v}"], [
    "DB_HOST=${var.db_host}",
    "DB_PORT=${var.db_port}"
  ])

  volumes {
    host_path      = abspath("${path.module}/../../wait-for-mysql.sh")
    container_path = "/usr/local/bin/wait-for-mysql.sh"
    read_only      = true
  }

  entrypoint = ["/usr/local/bin/wait-for-mysql.sh"]
  command = ["/opt/jboss/container/java/run/run-java.sh"]

  networks_advanced {
    name = var.network_name
  }

  restart = "always"
}


