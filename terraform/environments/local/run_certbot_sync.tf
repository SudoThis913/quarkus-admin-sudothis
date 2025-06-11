# Certbot Sync Container Terraform Definition

resource "docker_image" "certbot_sync_run" {
  name = "certbot-sync-run"

  build {
    context    = abspath("${path.module}/../../modules/certbot-sync")
    dockerfile = "Dockerfile"
  }
}

resource "docker_container" "certbot_sync_run" {
  name         = "certbot-sync-run"
  image        = docker_image.certbot_sync_run.name
  restart      = "no"
  must_run     = false
  rm           = false
  logs         = true
  attach       = true
  network_mode = "quarkus_net"

  env = [
    "DOMAIN=yourdomain.com",
    "EMAIL=example@example.com",
    "DNS_PROVIDER=cloudflare",
    "MODE=local", // Local returns self-signed cert if available, change to prod to certreq from letsencrypt.
    "DRY_RUN=false"
  ]

  volumes {
    host_path      = "/etc/letsencrypt"
    container_path = "/etc/letsencrypt"
  }

  volumes {
    host_path      = abspath("${path.module}/secrets/cloudflare.ini")
    container_path = "/secrets/cloudflare.ini"
    read_only      = true
  }

  volumes {
    host_path      = abspath("${path.module}/secrets/certsync")
    container_path = "/mnt/certsync"
  }

  volumes {
    host_path      = abspath("${path.module}/secrets/hosts.txt")
    container_path = "/mnt/hosts.txt"
    read_only      = true
  }

  depends_on = [docker_image.certbot_sync_run]
}
