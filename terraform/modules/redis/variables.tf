variable "name" {
  type = string
}

variable "network_name" {
  type = string
}

variable "port" {
  type    = number
  default = 6379
}

variable "password" {
  type    = string
  default = ""
}
