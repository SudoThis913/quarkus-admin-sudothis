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

  ports {
    internal = 3306
    external = var.port
  }

  networks_advanced {
    name = var.network_name
  }

  restart = "always"

  healthcheck {
    test         = ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 5
    start_period = "5s"
  }
}

resource "docker_container" "flyway_migration" {
  name  = "flyway-init"
  image = "flyway/flyway:10.11"
  depends_on = [docker_container.mysql]

  env = [
    "FLYWAY_URL=jdbc:mysql://${var.name}:3306/${var.database}",
    "FLYWAY_USER=${var.username}",
    "FLYWAY_PASSWORD=${var.password}",
    "FLYWAY_CONNECT_RETRIES=10",
    "FLYWAY_CONNECT_RETRIES_INTERVAL=3",
    "FLYWAY_CLEAN_DISABLED=false",
    "FLYWAY_LOCATIONS=filesystem:/flyway/sql"
  ]

  volumes {
    host_path      = abspath("${path.module}/flyway/sql")
    container_path = "/flyway/sql"
  }

  entrypoint = ["sh", "-c"]
  command = [<<-EOT
  echo "Listing migration files in /flyway/sql:"
  ls -l /flyway/sql || echo "No files found"
  echo "Waiting for MySQL..."
  for i in $(seq 1 20); do
    flyway info && break || echo "Waiting..."; sleep 3;
  done
  echo "Cleaning and migrating..."
  flyway clean
  flyway migrate
EOT
]

  networks_advanced {
    name = var.network_name
  }
}