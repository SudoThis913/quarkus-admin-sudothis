terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

resource "docker_image" "redis" {
  name = "redis:7.2.4"
}

resource "docker_container" "redis" {
  name  = var.name
  image = docker_image.redis.name
  networks_advanced {
    name = var.network_name
  }

  ports {
    internal = 6379
    external = var.port
  }

    env = [
        for key, value in var.env_vars : "${key}=${value}"
    ]

  command = var.password != "" ? ["redis-server", "--requirepass", var.password] : null

  restart = "unless-stopped"
}
