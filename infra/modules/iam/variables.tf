variable "prefixo" {
  type = string
}

variable "arn_tabela_avaliacoes" {
  type = string
}

variable "arn_identidade_ses_remetente" {
  type = string
}

variable "arn_parametro_email_admin" {
  type = string
}

variable "nome_barramento" {
  description = "Nome do barramento EventBridge (o ARN é construído aqui para evitar dependência circular com o módulo eventbridge)"
  type        = string
}
