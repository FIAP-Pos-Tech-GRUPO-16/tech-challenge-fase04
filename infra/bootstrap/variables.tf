variable "regiao" {
  description = "Região AWS onde os recursos de bootstrap serão criados"
  type        = string
  default     = "us-east-1"
}

variable "prefixo" {
  description = "Prefixo usado no nome do bucket e da tabela de lock"
  type        = string
  default     = "tech-challenge-fase04"
}
