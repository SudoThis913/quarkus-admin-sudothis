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

  env = concat([for k, v in var.env_vars : "${k}=${v}"], [
    "DB_HOST=${var.db_host}",
    "DB_PORT=${var.db_port}"
  ])

  volumes {
    host_path      = abspath("${path.module}/../../wait-for-mysql.sh")
    container_path = "/wait-for-mysql.sh"
    read_only      = true
  }

  entrypoint = ["/wait-for-mysql.sh"]
  command = ["/opt/jboss/container/java/run/run-java.sh"]

  networks_advanced {
    name = var.network_name
  }

  restart = "always"
}



