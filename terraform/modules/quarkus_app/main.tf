terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

resource "docker_container" "quarkus" {
  name  = var.name
  image = var.image

  ports {
    internal = var.internal_port
    external = var.external_port
  }

  env = [for k, v in var.env_vars : "${k}=${v}"]

  networks_advanced {
    name = var.network_name
  }

  restart = "always"
}
