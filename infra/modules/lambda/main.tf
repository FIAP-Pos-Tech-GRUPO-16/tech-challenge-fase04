resource "aws_cloudwatch_log_group" "funcao" {
  name              = "/aws/lambda/${var.nome_funcao}"
  retention_in_days = var.retencao_logs_dias

  tags = {
    Projeto = var.prefixo
  }
}

resource "aws_lambda_function" "funcao" {
  function_name = var.nome_funcao
  description   = var.descricao
  role          = var.arn_role

  filename         = var.caminho_jar
  source_code_hash = filebase64sha256(var.caminho_jar)

  # Handler fixo do adapter AWS do Spring Cloud Function: ele inicializa o contexto Spring
  # a partir da classe apontada em MAIN_CLASS e delega para o bean Function configurado.
  handler = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
  runtime = "java21"

  memory_size = var.memoria_mb
  timeout     = var.timeout_segundos

  environment {
    variables = merge(
      { MAIN_CLASS = var.classe_principal },
      var.variaveis_ambiente
    )
  }

  tags = {
    Projeto = var.prefixo
  }

  depends_on = [aws_cloudwatch_log_group.funcao]
}
