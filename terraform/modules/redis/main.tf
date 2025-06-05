resource "docker_image" "redis" {
  name = "redis:7"
}

resource "docker_container" "redis" {
  name  = var.name
  image = docker_image.redis.latest
  networks_advanced {
    name = var.network_name
  }

  ports {
    internal = 6379
    external = var.port
  }

  dynamic "env" {
    for_each = var.password != "" ? [1] : []
    content {
      name  = "REDIS_PASSWORD"
      value = var.password
    }
  }

  command = var.password != "" ? ["redis-server", "--requirepass", var.password] : null

  restart = "unless-stopped"
}
