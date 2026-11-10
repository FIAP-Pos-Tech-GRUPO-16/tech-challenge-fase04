variable "prefixo" {
  type = string
}

variable "email_admin_inicial" {
  type = string
}

variable "funcoes" {
  description = "Mapa nome-amigável -> nome real da função Lambda, usado nos alarmes e no dashboard"
  type        = map(string)
}

variable "dlqs" {
  description = "Mapa nome-amigável -> nome real da fila DLQ, usado nos alarmes e no dashboard"
  type        = map(string)
}

variable "timeout_funcao_segundos" {
  type = number
}

variable "nome_tabela_avaliacoes" {
  type = string
}

variable "id_api" {
  type = string
}
