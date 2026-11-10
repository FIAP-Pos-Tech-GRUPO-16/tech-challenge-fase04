variable "prefixo" {
  description = "Prefixo usado nas tags dos recursos"
  type        = string
}

variable "email_remetente" {
  description = "E-mail remetente a ser verificado no SES"
  type        = string
}

variable "email_admin_inicial" {
  description = "E-mail do administrador a ser verificado como destinatário inicial (modo sandbox)"
  type        = string
}
