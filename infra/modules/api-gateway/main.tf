resource "aws_apigatewayv2_api" "http_api" {
  name          = "${var.prefixo}-api"
  protocol_type = "HTTP"
  description   = "Entrada pública do desafio: POST /avaliacao"
}

resource "aws_cloudwatch_log_group" "acesso_api" {
  name              = "/aws/apigateway/${var.prefixo}-api"
  retention_in_days = var.retencao_logs_dias

  tags = { Projeto = var.prefixo }
}

resource "aws_apigatewayv2_integration" "receber_avaliacao" {
  api_id                 = aws_apigatewayv2_api.http_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = var.arn_invocacao_funcao_receber_avaliacao
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "post_avaliacao" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "POST /avaliacao"
  target    = "integrations/${aws_apigatewayv2_integration.receber_avaliacao.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.http_api.id
  name        = "$default"
  auto_deploy = true

  # Limite de requisições básico contra abuso — o endpoint é público e não exige
  # autenticação (não é um requisito do desafio), então essa é a principal defesa.
  default_route_settings {
    throttling_burst_limit = 10
    throttling_rate_limit  = 5
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.acesso_api.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip              = "$context.identity.sourceIp"
      requestTime     = "$context.requestTime"
      httpMethod      = "$context.httpMethod"
      routeKey        = "$context.routeKey"
      status          = "$context.status"
      responseLength  = "$context.responseLength"
      integrationError = "$context.integrationErrorMessage"
    })
  }
}

resource "aws_lambda_permission" "permitir_api_gateway" {
  statement_id  = "PermitirApiGateway"
  action        = "lambda:InvokeFunction"
  function_name = var.nome_funcao_receber_avaliacao
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}
