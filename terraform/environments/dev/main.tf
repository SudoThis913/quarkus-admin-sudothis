terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
    }
    tls = {
      source = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

locals {
  network_name = "dev_net"
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
  network_name = local.network_name
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
  port     = 3301
  network_name = local.network_name
}



module "quarkus_app" {
  source         = "../../modules/quarkus_app"
  providers = {docker = docker }
  name           = "dev-quarkus-admin"
  image          = "quarkus-admin:dev"
  internal_port = 8081
  external_port = 8001
  https_external_port = 8443
  network_name = local.network_name
  db_host        = "dev-quarkus-mysql"
  db_port        = 3306
  mysql_ca_cert_path = module.mysql.mysql_ca_cert_path
  
  env_vars = {
    QUARKUS_PROFILE = "dev"
    QUARKUS_DATASOURCE_JDBC_URL  = "jdbc:mysql://dev-quarkus-mysql:3306/quarkusdb" # This will be overridden by module, but kept for clarity
    QUARKUS_DATASOURCE_USERNAME = "admin"
    QUARKUS_DATASOURCE_PASSWORD = "admin123"
    QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION = "update"
    QUARKUS_REDIS_HOSTS = "redis://dev-quarkus-redis:6379"
    QUARKUS_OIDC_AUTH_SERVER_URL = "http://keycloak:8080/realms/quarkus"
  }
  depends_on = [module.mysql]
}

