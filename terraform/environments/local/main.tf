terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
    tls = {
      source = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

resource "docker_network" "app_net" {
  name = "local_net"
  lifecycle {
    prevent_destroy = false
    create_before_destroy = true
    ignore_changes = []
  }
}

module "mailhog" {
  source       = "../../modules/mailhog"
  providers    = { docker = docker }
  name         = "local-mailhog"
  network_name = docker_network.app_net.name
  smtp_port    = 1025
  ui_port      = 8025
}

module "redis" {
  source       = "../../modules/redis"
  providers    = { docker = docker }
  name         = "local-quarkus-redis"
  port         = 6379
  network_name = docker_network.app_net.name
  password     = var.redis_password
  env_vars = {
    REDIS_PASSWORD = var.redis_password
  }
}

module "mysql" {
  source       = "../../modules/mysql"
  providers    = { docker = docker }
  name         = "local-quarkus-mysql"
  username     = var.db_user
  password     = var.db_password
  database     = "quarkusdb"
  port         = 3307
  network_name = docker_network.app_net.name
}

module "keycloak" {
  source          = "../../modules/keycloak"
  providers       = { docker = docker }
  name            = "local-keycloak"
  network_name    = docker_network.app_net.name
  admin_user      = "admin"
  admin_password  = "admin"
  http_port       = 8180
  realm_file_path = abspath("${path.module}/../../src/main/resources/keycloak-realm.json")
  db_host         = "local-quarkus-mysql"
  db_port         = 3306
  
  depends_on = [module.mysql]
}

module "quarkus_app" {
  source         = "../../modules/quarkus_app"
  providers      = { docker = docker }
  name           = "local-quarkus-admin"
  image          = "quarkus-admin:dev"
  internal_port  = 8080
  external_port  = 8081
  network_name   = docker_network.app_net.name
  db_host        = "local-quarkus-mysql"
  db_port        = 3306
  mysql_ca_cert_path = module.mysql.mysql_ca_cert_path

  env_vars = {
    QUARKUS_PROFILE                          = "dev"
    QUARKUS_DATASOURCE_JDBC_URL              = "jdbc:mysql://local-quarkus-mysql:3306/quarkusdb"
    QUARKUS_DATASOURCE_USERNAME              = var.db_user
    QUARKUS_DATASOURCE_PASSWORD              = var.db_password
    QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION = "update"
    SMTP_HOST                                 = "mailhog"
    SMTP_PORT                                 = "1025"
    QUARKUS_OIDC_AUTH_SERVER_URL              = "http://local-keycloak:8080/realms/sudothis"
    QUARKUS_REDIS_HOSTS                       = "redis://local-quarkus-redis:6379"
  }

  depends_on = [module.mysql, module.redis, module.keycloak]
}

/**
module "certbot_sync" {
  source           = "../../modules/certbot-sync"
  name             = "certbot-sync"
  domain           = "yourdomain.com"
  email            = "admin@yourdomain.com"
  dns_provider     = "cloudflare"
  mode             = "dev"
  credentials_path = "${path.module}/secrets/cloudflare.ini"
  certsync_path    = "${path.module}/secrets/certsync"
  host_list_path   = "${path.module}/secrets/hosts.txt"
  network_name     = docker_network.app_net.name

  build_context    = "${path.module}/../../../../sudothis/quarkus-admin-sudothis/containers/certbot-sync"
} **/
