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

variable "cognito_issuer" {
  description = "Issuer do User Pool do Cognito, usado pelo JWT Authorizer"
  type        = string
}

variable "cognito_client_id" {
  description = "Client ID do User Pool do Cognito, usado como audience pelo JWT Authorizer"
  type        = string
}
