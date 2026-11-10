output "id_user_pool" {
  value = aws_cognito_user_pool.estudantes.id
}

output "arn_user_pool" {
  value = aws_cognito_user_pool.estudantes.arn
}

output "id_client" {
  value = aws_cognito_user_pool_client.api_estudantes.id
}

output "issuer" {
  description = "Issuer usado pelo JWT Authorizer do API Gateway para validar o token"
  value       = "https://cognito-idp.${data.aws_region.atual.name}.amazonaws.com/${aws_cognito_user_pool.estudantes.id}"
}
