terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

# Create a local directory to store certificates
resource "null_resource" "certs_dir" {
  provisioner "local-exec" {
    command = "mkdir -p ${path.module}/certs"
  }
  triggers = {
    always_run = timestamp()
  }
}

# 1. CA Certificate
resource "tls_private_key" "ca_private_key" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "ca_cert" {
  private_key_pem = tls_private_key.ca_private_key.private_key_pem

  subject {
    common_name = "MySQL CA"
    organization = "SudoThis"
  }

  is_ca_certificate = true
  validity_period_hours = 8760 # 1 year
  set_subject_key_id = true

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "cert_signing",
  ]
}

resource "local_file" "ca_pem" {
  content  = tls_self_signed_cert.ca_cert.cert_pem
  filename = "${path.module}/certs/ca.pem"
  depends_on = [null_resource.certs_dir]
}

# 2. Server Certificate
resource "tls_private_key" "server_private_key" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_cert_request" "server_csr" {
  private_key_pem = tls_private_key.server_private_key.private_key_pem

  subject {
    common_name = var.name # MySQL container name
    organization = "SudoThis"
  }

  dns_names = [var.name, "localhost"] # Allow connection to container name and localhost
  ip_addresses = ["127.0.0.1"]
}

resource "tls_locally_signed_cert" "server_cert" {
  cert_request_pem   = tls_cert_request.server_csr.cert_request_pem
  ca_private_key_pem = tls_private_key.ca_private_key.private_key_pem
  ca_cert_pem        = tls_self_signed_cert.ca_cert.cert_pem

  validity_period_hours = 8760 # 1 year
  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]
}

resource "local_file" "server_pem" {
  content  = tls_locally_signed_cert.server_cert.cert_pem
  filename = "${path.module}/certs/server.pem"
  depends_on = [null_resource.certs_dir]
}

resource "local_file" "server_key" {
  content  = tls_private_key.server_private_key.private_key_pem
  filename = "${path.module}/certs/server.key"
  depends_on = [null_resource.certs_dir]
}

# 3. Client Certificate (Optional, for mutual TLS)
resource "tls_private_key" "client_private_key" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_cert_request" "client_csr" {
  private_key_pem = tls_private_key.client_private_key.private_key_pem

  subject {
    common_name = "MySQL Client"
    organization = "SudoThis"
  }
}

resource "tls_locally_signed_cert" "client_cert" {
  cert_request_pem   = tls_cert_request.client_csr.cert_request_pem
  ca_private_key_pem = tls_private_key.ca_private_key.private_key_pem
  ca_cert_pem        = tls_self_signed_cert.ca_cert.cert_pem

  validity_period_hours = 8760 # 1 year
  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "client_auth",
  ]
}

resource "local_file" "client_pem" {
  content  = tls_locally_signed_cert.client_cert.cert_pem
  filename = "${path.module}/certs/client.pem"
  depends_on = [null_resource.certs_dir]
}

resource "local_file" "client_key" {
  content  = tls_private_key.client_private_key.private_key_pem
  filename = "${path.module}/certs/client.key"
  depends_on = [null_resource.certs_dir]
}


resource "docker_image" "mysql" {
  name = "mysql:8"
}

resource "docker_container" "mysql" {
  name  = var.name
  image = docker_image.mysql.name
  depends_on = [docker_image.mysql, local_file.ca_pem, local_file.server_pem, local_file.server_key]
  
  # Enable mysql_native_password plugin for compatibility
  command = ["mysqld", "--mysql-native-password=ON"]

  env = compact([
    "MYSQL_ROOT_PASSWORD=${var.password}",
    "MYSQL_DATABASE=${var.database}",
    "MYSQL_USER=${var.username}",
    "MYSQL_PASSWORD=${var.password}",
    "MYSQL_ROOT_HOST=%", # Allow root connections from any host
    "MYSQL_SSL_CA=/certs/ca.pem",
    "MYSQL_SSL_CERT=/certs/server.pem",
    "MYSQL_SSL_KEY=/certs/server.key",
    "MYSQL_DEFAULT_AUTHENTICATION_PLUGIN=mysql_native_password",
    var.additional_databases != null && length(var.additional_databases) > 0 ? "MYSQL_ADDITIONAL_DATABASES=${join(",", var.additional_databases)}" : null
  ])

  ports {
    internal = 3306
    external = var.port
  }

  networks_advanced {
    name = var.network_name
  }

  volumes {
    host_path      = abspath("${path.module}/certs/ca.pem")
    container_path = "/certs/ca.pem"
    read_only      = true
  }
  volumes {
    host_path      = abspath("${path.module}/certs/server.pem")
    container_path = "/certs/server.pem"
    read_only      = true
  }
  volumes {
    host_path      = abspath("${path.module}/certs/server.key")
    container_path = "/certs/server.key"
    read_only      = true
  }
  volumes {
    host_path      = abspath("${path.module}/init")
    container_path = "/docker-entrypoint-initdb.d"
    read_only      = true
  }

  restart = "always"

  healthcheck {
    test         = ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 5
    start_period = "30s"
  }


}

resource "docker_container" "flyway_migration" {
  name  = "${var.name}-flyway"
  image = "flyway/flyway:10.11"
  depends_on = [docker_container.mysql, local_file.ca_pem]

  env = [
    "FLYWAY_URL=jdbc:mysql://${var.name}:3306/${var.database}?useSSL=true&requireSSL=true&serverRsaPublicKeyFile=/certs/ca.pem",
    "FLYWAY_USER=${var.username}",
    "FLYWAY_PASSWORD=${var.password}",
    "FLYWAY_CONNECT_RETRIES=10",
    "FLYWAY_CONNECT_RETRIES_INTERVAL=3",
    "FLYWAY_CLEAN_DISABLED=false",
    "FLYWAY_LOCATIONS=filesystem:/flyway/sql"
  ]

  volumes {
    host_path      = abspath("${path.module}/flyway/sql")
    container_path = "/flyway/sql"
  }
  volumes {
    host_path      = abspath("${path.module}/certs/ca.pem")
    container_path = "/certs/ca.pem"
    read_only      = true
  }

  entrypoint = ["sh", "-c"]
  command = [<<-EOT
  echo "Listing migration files in /flyway/sql:"
  ls -l /flyway/sql || echo "No files found"
  echo "Waiting for MySQL..."
  for i in $(seq 1 20); do
    flyway info && break || echo "Waiting..."; sleep 3;
  done
  echo "Cleaning and migrating..."
  flyway clean
  flyway migrate
EOT
]

  networks_advanced {
    name = var.network_name
  }
}