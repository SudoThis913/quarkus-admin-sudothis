variable "name" {}
variable "image" {}
variable "internal_port" {
  type = number
}
variable "external_port" {
  type = number
}
variable "network_name" {
  type = string
}
variable "env_vars" {
  type = map(string)
}
