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

# O SnapStart só se aplica a versões publicadas — quem invoca a função precisa usar o alias
# (nunca a função base/$LATEST) para o cold start reduzido realmente acontecer.
output "nome_alias" {
  value = aws_lambda_alias.live.name
}

output "arn_alias" {
  value = aws_lambda_alias.live.arn
}

output "arn_invocacao_alias" {
  value = aws_lambda_alias.live.invoke_arn
}
