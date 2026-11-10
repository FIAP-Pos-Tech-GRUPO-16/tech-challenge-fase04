output "nome_funcao" {
  value = aws_lambda_function.funcao.function_name
}

output "arn_funcao" {
  value = aws_lambda_function.funcao.arn
}

output "arn_invocacao" {
  value = aws_lambda_function.funcao.invoke_arn
}

output "nome_grupo_logs" {
  value = aws_cloudwatch_log_group.funcao.name
}
