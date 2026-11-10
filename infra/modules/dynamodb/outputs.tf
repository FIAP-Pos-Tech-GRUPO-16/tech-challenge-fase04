output "nome_tabela" {
  value = aws_dynamodb_table.avaliacoes.name
}

output "arn_tabela" {
  value = aws_dynamodb_table.avaliacoes.arn
}

output "nome_indice_por_data" {
  value = "gsi_data_envio"
}
