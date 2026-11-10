output "nome_parametro" {
  value = aws_ssm_parameter.email_admin.name
}

output "arn_parametro" {
  value = aws_ssm_parameter.email_admin.arn
}
