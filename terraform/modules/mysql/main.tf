terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

resource "docker_image" "mysql" {
  name = "mysql:8"
}

resource "docker_container" "mysql" {
  name  = var.name
  image = docker_image.mysql.name
  depends_on = [docker_image.mysql]

  env = [
    "MYSQL_ROOT_PASSWORD=${var.password}",
    "MYSQL_DATABASE=${var.database}",
    "MYSQL_USER=${var.username}",
    "MYSQL_PASSWORD=${var.password}"
  ]

  volumes {
    host_path      = abspath("${path.module}/init/init.sql")
    container_path = "/docker-entrypoint-initdb.d/init.sql"
  }

  ports {
    internal = 3307
    external = var.port
  }

  networks_advanced {
    name = var.network_name
  }

  restart = "always"

  healthcheck {
    test     = ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval = "10s"
    timeout  = "5s"
    retries  = 5
    start_period = "5s"
  }
}
