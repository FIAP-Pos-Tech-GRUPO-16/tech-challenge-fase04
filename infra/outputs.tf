output "url_api" {
  description = "URL base da API — POST {url_api}/avaliacao"
  value       = module.api_gateway.url_api
}

output "url_dashboard" {
  description = "Dashboard CloudWatch com a visão geral do sistema"
  value       = module.cloudwatch.url_dashboard
}

output "nome_tabela_avaliacoes" {
  value = module.dynamodb.nome_tabela
}

output "cognito_user_pool_id" {
  description = "ID do User Pool — usado para criar usuários de teste (AdminCreateUser)"
  value       = module.cognito.id_user_pool
}

output "cognito_client_id" {
  description = "Client ID do User Pool — usado para autenticar (InitiateAuth) e como audience do JWT"
  value       = module.cognito.id_client
}
