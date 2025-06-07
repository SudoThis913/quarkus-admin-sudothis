terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
    }
  }
}

resource "docker_network" "app_net" {
  name = "quarkus_net"
  lifecycle {
    prevent_destroy = false
    create_before_destroy = true
    ignore_changes = []
  }
}

resource "docker_container" "mailhog" {
  name         = "mailhog"
  image        = "mailhog/mailhog"
  restart      = "unless-stopped"
  networks_advanced {
    name = docker_network.app_net.name
  }

  ports {
    internal = 1025
    external = 1025
  }

  ports {
    internal = 8025
    external = 8025
  }
}

module "redis" {
  source       = "../../modules/redis"
  providers    = { docker = docker }
  name         = "quarkus-redis"
  port         = 6379
  network_name = docker_network.app_net.name
  password     = "letmein" # optional; remove or change, definitely change before using in prod
  
  env_vars = {
    REDIS_PASSWORD = "letmein"
  }
}

module "mysql" {
  source   = "../../modules/mysql"
  providers = {docker = docker}
  name     = "quarkus-mysql"
  username = "admin"
  password = "admin123"
  database = "quarkusdb"
  port     = 3307
  network_name = docker_network.app_net.name
}

module "quarkus_app" {
  source         = "../../modules/quarkus_app"
  providers = {docker = docker }
  name           = "quarkus-admin"
  image          = "quarkus-admin:dev"
  internal_port = 8080
  external_port = 8081
  network_name = docker_network.app_net.name
  
  env_vars = {
    QUARKUS_PROFILE = "dev"
    QUARKUS_DATASOURCE_JDBC_URL  = "jdbc:mysql://quarkus-mysql:3306/quarkusdb"
    QUARKUS_DATASOURCE_USERNAME = "admin"
    QUARKUS_DATASOURCE_PASSWORD = "admin123"
    QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION = "update"
    SMTP_HOST                             = "mailhog"
    SMTP_PORT                             = "1025"
  }
  depends_on = [module.mysql]
}


module "mailhog" {
  source       = "../../modules/mailhog"
  providers = {docker = docker }
  name         = "mailhog"
  network_name = docker_network.app_net.name
  smtp_port    = 1025
  ui_port      = 8025
}
