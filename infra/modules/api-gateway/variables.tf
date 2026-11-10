variable "prefixo" {
  type = string
}

variable "arn_invocacao_funcao_receber_avaliacao" {
  type = string
}

variable "nome_funcao_receber_avaliacao" {
  type = string
}

variable "alias_funcao_receber_avaliacao" {
  description = "Nome do alias Lambda (\"live\") usado para habilitar o SnapStart"
  type        = string
}

variable "retencao_logs_dias" {
  type = number
}
