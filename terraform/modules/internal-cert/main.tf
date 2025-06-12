# TLS Certificate Generation Module with ECC

variable "common_name" {
  description = "Common name for the certificate (e.g., service name)"
  type        = string
}

variable "cert_dir" {
  description = "Directory to write cert files to"
  type        = string
}

locals {
  cert_path = abspath(var.cert_dir)
}

resource "tls_private_key" "ecc" {
  algorithm   = "ECDSA"
  ecdsa_curve = "P384"
}

resource "tls_self_signed_cert" "ecc" {
  count             = fileexists("${local.cert_path}/server-cert.pem") && timeadd(timestamp(), "219000h") < timestamp() ? 0 : 1
  private_key_pem   = tls_private_key.ecc.private_key_pem

  subject {
    common_name  = var.common_name
    organization = "SudoThis"
  }

  validity_period_hours = 219000 # ~25 years
  is_ca_certificate     = true

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "key_agreement",
    "server_auth",
    "client_auth"
  ]
}

resource "local_file" "cert_pem" {
  count    = length(tls_self_signed_cert.ecc) > 0 ? 1 : 0
  content  = tls_self_signed_cert.ecc[0].cert_pem
  filename = "${local.cert_path}/server-cert.pem"
}

resource "local_file" "key_pem" {
  count    = length(tls_self_signed_cert.ecc) > 0 ? 1 : 0
  content  = tls_private_key.ecc.private_key_pem
  filename = "${local.cert_path}/server-key.pem"
}

resource "local_file" "ca_pem" {
  count    = length(tls_self_signed_cert.ecc) > 0 ? 1 : 0
  content  = tls_self_signed_cert.ecc[0].cert_pem
  filename = "${local.cert_path}/ca.pem"
}

output "cert_path" {
  value = local.cert_path
}
