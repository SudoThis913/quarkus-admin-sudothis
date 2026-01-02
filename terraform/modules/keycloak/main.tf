# main.tf for keycloak module

terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

# Keycloak Docker Image
resource "docker_image" "keycloak" {
  name = "quay.io/keycloak/keycloak:latest" # Using the official Quarkus Keycloak image
}

# Keycloak Docker Container
resource "docker_container" "keycloak" {
  name  = var.name
  image = docker_image.keycloak.name

  # Initial admin user setup
  env = [
    "KEYCLOAK_ADMIN=${var.admin_user}",
    "KEYCLOAK_ADMIN_PASSWORD=${var.admin_password}",
    "KC_DB=mysql", # Point to the MySQL container within the network
    "KC_DB_URL=jdbc:mysql://${var.db_host}:${var.db_port}/keycloak", # Assuming a separate DB for Keycloak
    "KC_DB_USERNAME=admin",
    "KC_DB_PASSWORD=admin123", # Using the same admin password for DB for simplicity
    "KC_HOSTNAME=${var.name}",
    "KC_HTTP_PORT=8080", # Keycloak's internal HTTP port
    "KC_HTTPS_PORT=8443", # Keycloak's internal HTTPS port
    "KC_PROXY=edge",
    "KC_HEALTH_ENABLED=true",
    "KC_METRICS_ENABLED=true",
    "KC_LOG_LEVEL=INFO",
    "KC_DB_SCHEMA=keycloak",
    "KC_MIGRATION_STRATEGY=update"
  ]

  # Expose external HTTP port
  ports {
    internal = 8080
    external = var.http_port
  }

  networks_advanced {
    name = var.network_name
    aliases = [var.name]
  }

  # Command to start Keycloak and import realm on first run
  entrypoint = ["/bin/bash", "-c"]
  command = ["/usr/local/bin/wait-for-it.sh ${var.db_host}:${var.db_port} -- /opt/keycloak/bin/kc.sh start-dev --import-realm"]

  # Volume to mount the realm JSON file for import
  volumes {
    host_path      = var.realm_file_path
    container_path = "/opt/keycloak/data/import/realm.json"
    read_only      = true
  }
  volumes {
    host_path      = abspath("${path.module}/../../../wait-for-mysql.sh")
    container_path = "/usr/local/bin/wait-for-it.sh"
    read_only      = true
  }

  restart = "unless-stopped"




}
