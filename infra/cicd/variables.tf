variable "regiao" {
  description = "Região única do projeto na nova experiência AWS"
  type        = string
  default     = "us-east-2"
}

variable "prefixo" {
  description = "Prefixo dos recursos da aplicação (o mesmo usado em infra/)"
  type        = string
  default     = "tech-challenge-fase04"
}

variable "repositorio_github" {
  description = "Repositório no formato organização/nome, lido pelo pipeline via CodeConnections"
  type        = string
  default     = "FIAP-Pos-Tech-GRUPO-16/tech-challenge-fase04"
}

variable "branch" {
  description = "Branch cujo push dispara o deploy"
  type        = string
  default     = "main"
}

variable "email_admin_inicial" {
  description = "Repassado ao terraform apply da aplicação (TF_VAR_email_admin_inicial)"
  type        = string
}

variable "email_remetente" {
  description = "Repassado ao terraform apply da aplicação (TF_VAR_email_remetente)"
  type        = string
}

variable "bucket_state_terraform" {
  description = "Bucket do state remoto (criado por infra/bootstrap/)"
  type        = string
  default     = "tech-challenge-fase04-terraform-state-827938108008"
}

variable "tabela_lock_terraform" {
  description = "Tabela DynamoDB de lock do state (criada por infra/bootstrap/)"
  type        = string
  default     = "tech-challenge-fase04-terraform-lock"
}
