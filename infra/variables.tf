variable "regiao" {
  description = "Região AWS onde os recursos serão criados. Conta usa a 'nova experiência AWS' (sign-up social, baseada em projeto) — todo recurso regional é travado na região única do projeto, sem exceção."
  type        = string
  default     = "us-east-2"
}

variable "prefixo" {
  description = "Prefixo usado no nome de todos os recursos, para evitar colisão em contas compartilhadas"
  type        = string
  default     = "tech-challenge-fase04"
}

variable "email_admin_inicial" {
  description = "E-mail do administrador que recebe alertas e relatórios. Guardado no SSM Parameter Store e pode ser trocado depois sem novo deploy (terraform ignora mudanças manuais nesse valor)."
  type        = string
}

variable "email_remetente" {
  description = "E-mail remetente verificado no SES para o envio de alertas e relatórios"
  type        = string
}

variable "retencao_logs_dias" {
  description = "Retenção dos logs das funções no CloudWatch Logs"
  type        = number
  default     = 30
}

variable "memoria_funcao_mb" {
  description = "Memória alocada para cada função Lambda (MB)"
  type        = number
  default     = 1024
}

variable "timeout_funcao_segundos" {
  description = "Timeout de cada função Lambda (segundos)"
  type        = number
  default     = 30
}
