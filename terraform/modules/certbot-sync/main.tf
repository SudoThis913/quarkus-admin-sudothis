terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

resource "docker_image" "certbot_sync" {
  name = var.name
  build {
    context    = var.build_context
    dockerfile = "Dockerfile"
    build_args = {
      FORCE_REBUILD = uuid()
    }
  }
}

resource "docker_container" "certbot_sync" {
  count        = var.mode == "prod" ? 1 : 0
  name         = var.name
  image        = docker_image.certbot_sync.name
  restart      = "no"

  volumes {
    host_path      = "/etc/letsencrypt"
    container_path = "/etc/letsencrypt"
  }

  volumes {
    host_path      = abspath(var.credentials_path)
    container_path = "/secrets/cloudflare.ini"
    read_only      = true
  }

  volumes {
    host_path      = abspath(var.certsync_path)
    container_path = "/mnt/certsync"
  }

  volumes {
    host_path      = abspath(var.host_list_path)
    container_path = "/mnt/hosts.txt"
    read_only      = true
  }

  env = [
    "DOMAIN=${var.domain}",
    "EMAIL=${var.email}",
    "DNS_PROVIDER=${var.dns_provider}",
    "MODE=${var.mode}",
    "DRY_RUN=true"
  ]

  network_mode = var.network_name
  depends_on   = [docker_image.certbot_sync]
}
