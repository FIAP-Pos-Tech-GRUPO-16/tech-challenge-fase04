output "nome_pipeline" {
  value = aws_codepipeline.deploy.name
}

output "arn_conexao_github" {
  description = "Conexão criada como PENDING: precisa ser aprovada uma única vez no console (ver docs/deploy.md)"
  value       = aws_codestarconnections_connection.github.arn
}

output "status_conexao_github" {
  value = aws_codestarconnections_connection.github.connection_status
}
