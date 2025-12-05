terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
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

module "mailhog" {
  source       = "../../modules/mailhog"
  providers    = { docker = docker }
  name         = "mailhog"
  network_name = docker_network.app_net.name
  smtp_port    = 1025
  ui_port      = 8025
}

module "redis" {
  source       = "../../modules/redis"
  providers    = { docker = docker }
  name         = "quarkus-redis"
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
  name         = "quarkus-mysql"
  username     = var.db_user
  password     = var.db_password
  database     = "quarkusdb"
  port         = 3307
  network_name = docker_network.app_net.name
}

module "quarkus_app" {
  source         = "../../modules/quarkus_app"
  providers      = { docker = docker }
  name           = "quarkus-admin"
  image          = var.app_image
  internal_port  = 8080
  external_port  = 8081
  network_name   = docker_network.app_net.name
  db_host        = "quarkus-mysql" # Default name in prod for the service
  db_port        = 3306

  env_vars = {
    QUARKUS_PROFILE                          = "prod"
    QUARKUS_DATASOURCE_JDBC_URL              = "jdbc:mysql://quarkus-mysql:3306/quarkusdb"
    QUARKUS_DATASOURCE_USERNAME              = var.db_user
    QUARKUS_DATASOURCE_PASSWORD              = var.db_password
    QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION = "update"
    QUARKUS_REDIS_HOSTS                      = "redis://quarkus-redis:6379"
    QUARKUS_REDIS_PASSWORD                   = var.redis_password
    SMTP_HOST                                 = "mailhog"
    SMTP_PORT                                 = "1025"
  }

  depends_on = [module.mysql, module.redis]
}

module "certbot_sync" {
  source           = "../../modules/certbot-sync"
  name             = "certbot-sync"
  domain           = "sudothis.com"
  email            = "gregn913@gmail.com"
  dns_provider     = "cloudflare"
  mode             = "prod"
  credentials_path = "${path.module}/secrets/cloudflare.ini"
  certsync_path    = "${path.module}/secrets/certsync"
  host_list_path   = "${path.module}/secrets/hosts.txt"
  network_name     = docker_network.app_net.name

  build_context    = "${path.module}/../../../../sudothis/quarkus-admin-sudothis/containers/certbot-sync"
}