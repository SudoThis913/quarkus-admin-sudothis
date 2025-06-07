# File: modules/mailhog/main.tf

resource "docker_image" "mailhog" {
  name          = "mailhog/mailhog:latest"
  keep_locally  = false
}

resource "docker_container" "mailhog" {
  name    = var.name
  image   = docker_image.mailhog.name
  restart = "unless-stopped"

lifecycle {
    replace_triggered_by = [docker_image.mailhog]
  }

  networks_advanced {
    name = var.network_name
  }

  ports {
    internal = 1025
    external = var.smtp_port
  }

  ports {
    internal = 8025
    external = var.ui_port
  }

  dynamic "labels" {
    for_each = [1] # workaround for literal block since only one label block is needed
    content {
      label = "app"
      value = "mailhog"
    }
  }

  dynamic "labels" {
    for_each = [1]
    content {
      label = "purpose"
      value = "smtp testing"
    }
  }
}
