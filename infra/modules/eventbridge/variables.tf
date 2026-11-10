variable "prefixo" {
  type = string
}

variable "nome_barramento" {
  type = string
}

variable "arn_funcao_notificar_urgencia" {
  type = string
}

variable "nome_funcao_notificar_urgencia" {
  type = string
}

variable "alias_funcao_notificar_urgencia" {
  description = "Nome do alias Lambda (\"live\") usado para habilitar o SnapStart"
  type        = string
}

variable "arn_funcao_gerar_relatorio_semanal" {
  type = string
}

variable "nome_funcao_gerar_relatorio_semanal" {
  type = string
}

variable "alias_funcao_gerar_relatorio_semanal" {
  description = "Nome do alias Lambda (\"live\") usado para habilitar o SnapStart"
  type        = string
}
