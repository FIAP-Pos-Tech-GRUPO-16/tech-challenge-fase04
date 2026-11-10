output "bucket_state" {
  description = "Nome do bucket S3 com o state remoto"
  value       = aws_s3_bucket.terraform_state.id
}

output "tabela_lock" {
  description = "Nome da tabela DynamoDB usada para lock do state"
  value       = aws_dynamodb_table.terraform_lock.name
}
