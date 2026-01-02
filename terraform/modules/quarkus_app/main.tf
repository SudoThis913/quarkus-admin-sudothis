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
  dynamic "ports" {
    for_each = var.https_external_port != null ? [1] : []
    content {
      internal = 8443 # Default HTTPS port for Quarkus
      external = var.https_external_port
    }
  }

  env = concat([for k, v in var.env_vars : "${k}=${v}"], [
    "DB_HOST=${var.db_host}",
    "DB_PORT=${var.db_port}",
    # Update JDBC URL to use SSL
    "QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://${var.db_host}:${var.db_port}/quarkusdb?useSSL=true&requireSSL=true&serverRsaPublicKeyFile=/certs/ca.pem"
  ])

  volumes {
    host_path      = abspath("${path.module}/../../..")
    container_path = "/app_scripts"
    read_only      = true
  }
  volumes {
    host_path      = var.mysql_ca_cert_path
    container_path = "/certs/ca.pem"
    read_only      = true
  }
  volumes {
    host_path      = abspath("${path.module}/../../../dev-keystore.p12")
    container_path = "/deployments/dev-keystore.p12"
    read_only      = true
  }
  volumes {
    host_path      = abspath("${path.module}/../../../src/main/resources/application.properties")
    container_path = "/deployments/config/application.properties"
    read_only      = true
  }

  entrypoint = ["/app_scripts/wait-for-mysql.sh"]
  command = ["${var.db_host}:${var.db_port}", "--", "/opt/jboss/container/java/run/run-java.sh"]

  networks_advanced {
    name = var.network_name
  }

  restart = "always"


}



