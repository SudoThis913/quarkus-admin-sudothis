terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
    }
  }
}

resource "docker_network" "app_net" {
  name = "dev_net"
  lifecycle {
    prevent_destroy = false
    create_before_destroy = true
    ignore_changes = []
  }
}

module "redis" {
  source       = "../../modules/redis"
  providers    = { docker = docker }
  name         = "dev-quarkus-redis"
  port         = 6379
  network_name = docker_network.app_net.name
  password     = "letmein" 
  
  env_vars = {
    REDIS_PASSWORD = "letmein"
  }
}

module "mysql" {
  source   = "../../modules/mysql"
  providers = {docker = docker}
  name     = "dev-quarkus-mysql"
  username = "admin"
  password = "admin123"
  database = "quarkusdb"
  port     = 3307
  network_name = docker_network.app_net.name
}

module "quarkus_app" {
  source         = "../../modules/quarkus_app"
  providers = {docker = docker }
  name           = "dev-quarkus-admin"
  image          = "quarkus-admin:dev"
  internal_port = 8080
  external_port = 8082
  network_name = docker_network.app_net.name
  db_host        = "dev-quarkus-mysql"
  db_port        = 3306
  
  env_vars = {
    QUARKUS_PROFILE = "dev"
    QUARKUS_DATASOURCE_JDBC_URL  = "jdbc:mysql://dev-quarkus-mysql:3306/quarkusdb"
    QUARKUS_DATASOURCE_USERNAME = "admin"
    QUARKUS_DATASOURCE_PASSWORD = "admin123"
    QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION = "update"
    QUARKUS_REDIS_HOSTS = "redis://dev-quarkus-redis:6379"
  }
  depends_on = [module.mysql]
}

