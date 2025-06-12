variable "name" {
  description = "Name for the certbot container and image"
  type        = string
  default     = "certbot-sync-run"
}

variable "domain" {
  description = "Domain to request certificates for (e.g., example.com)"
  type        = string
}

variable "email" {
  description = "Email address for Let's Encrypt notifications"
  type        = string
}

variable "dns_provider" {
  description = "DNS provider to use with certbot (e.g., cloudflare)"
  type        = string
}

variable "mode" {
  description = "Mode for certbot behavior: dev, prod, or auto"
  type        = string
  default     = "auto"
}

variable "credentials_path" {
  description = "Host path to DNS provider credentials"
  type        = string
}

variable "certsync_path" {
  description = "Local mount path where certs will be pushed to remote hosts"
  type        = string
}

variable "host_list_path" {
  description = "Path to file containing list of hostnames to sync to"
  type        = string
}

variable "network_name" {
  description = "Docker network the container should join"
  type        = string
}

variable "build_context" {
  description = "Absolute path to the Docker build context"
  type        = string
}
