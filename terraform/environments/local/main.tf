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

module "redis" {
  source       = "../../modules/redis"
  providers    = { docker = docker }
  name         = "quarkus-redis"
  port         = 6379
  network_name = docker_network.app_net.name
  password     = "letmein" # optional; remove or change
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
    QUARKUS_PROFILE = "local"
    QUARKUS_DATASOURCE_JDBC_URL  = "jdbc:mysql://quarkus-mysql:3306/quarkusdb"
    QUARKUS_DATASOURCE_USERNAME = "admin"
    QUARKUS_DATASOURCE_PASSWORD = "admin123"
    QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION = "update"
  }
  depends_on = [module.mysql]
}

