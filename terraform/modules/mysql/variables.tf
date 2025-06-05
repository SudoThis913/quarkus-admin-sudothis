variable "name" {
  default = "quarkus-mysql"
}

variable "image" {
  type    = string
  default = "mysql:8"
}

variable "port" {
  default = 3307
}
variable "username" {
  default = "admin"
}
variable "password" {
  default = "admin123"
}
variable "database" {
  default = "quarkusdb"
}
variable "network_name" {
  type = string
}

