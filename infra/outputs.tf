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
