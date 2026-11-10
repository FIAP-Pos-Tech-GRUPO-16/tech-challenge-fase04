variable "prefixo" {
  type = string
}

variable "nome_funcao" {
  description = "Nome completo da função Lambda"
  type        = string
}

variable "descricao" {
  type = string
}

variable "arn_role" {
  description = "ARN da IAM role de execução desta função"
  type        = string
}

variable "caminho_jar" {
  description = "Caminho local do jar sombreado (maven-shade-plugin) da função"
  type        = string
}

variable "classe_principal" {
  description = "FQN da classe @SpringBootApplication desta função (vira a env var MAIN_CLASS)"
  type        = string
}

variable "memoria_mb" {
  type = number
}

variable "timeout_segundos" {
  type = number
}

variable "variaveis_ambiente" {
  description = "Variáveis de ambiente adicionais da função (além de MAIN_CLASS)"
  type        = map(string)
  default     = {}
}

variable "retencao_logs_dias" {
  type = number
}
