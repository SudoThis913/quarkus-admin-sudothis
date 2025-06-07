# File: modules/mailhog/providers.tf

terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

provider "docker" {
  alias = "docker"
}
