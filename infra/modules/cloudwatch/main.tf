data "aws_region" "atual" {}

resource "aws_sns_topic" "alertas_operacionais" {
  name = "${var.prefixo}-alertas-operacionais"
}

resource "aws_sns_topic_subscription" "email_admin" {
  topic_arn = aws_sns_topic.alertas_operacionais.arn
  protocol  = "email"
  endpoint  = var.email_admin_inicial

  # A AWS envia um e-mail de confirmação de inscrição — precisa ser clicado manualmente,
  # igual à verificação de identidade do SES (ver docs/deploy.md).
}

# ---------------------------------------------------------------------------
# Alarmes por função: erro, throttling e duração perto do timeout.
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "erros" {
  for_each = var.funcoes

  alarm_name          = "${var.prefixo}-${each.key}-erros"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Função ${each.key} lançou erro nos últimos 5 minutos"
  alarm_actions       = [aws_sns_topic.alertas_operacionais.arn]
  ok_actions          = [aws_sns_topic.alertas_operacionais.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = each.value
  }
}

resource "aws_cloudwatch_metric_alarm" "throttles" {
  for_each = var.funcoes

  alarm_name          = "${var.prefixo}-${each.key}-throttles"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Throttles"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Função ${each.key} sofreu throttling nos últimos 5 minutos"
  alarm_actions       = [aws_sns_topic.alertas_operacionais.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = each.value
  }
}

resource "aws_cloudwatch_metric_alarm" "duracao" {
  for_each = var.funcoes

  alarm_name          = "${var.prefixo}-${each.key}-duracao-proxima-do-timeout"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Duration"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Maximum"
  threshold           = var.timeout_funcao_segundos * 1000 * 0.8
  alarm_description   = "Função ${each.key} está demorando perto do timeout configurado"
  alarm_actions       = [aws_sns_topic.alertas_operacionais.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = each.value
  }
}

# ---------------------------------------------------------------------------
# Alarmes de DLQ: mensagem parada na fila = algo falhou mesmo após retry.
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "dlq_com_mensagens" {
  for_each = var.dlqs

  alarm_name          = "${var.prefixo}-dlq-${each.key}-com-mensagens"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "Mensagens acumuladas na DLQ de ${each.key} — algo falhou mesmo após retry"
  alarm_actions       = [aws_sns_topic.alertas_operacionais.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = each.value
  }
}

# ---------------------------------------------------------------------------
# Dashboard único consolidando as 3 funções, o DynamoDB e a API.
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_dashboard" "principal" {
  dashboard_name = "${var.prefixo}-visao-geral"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Invocações por função"
          region  = data.aws_region.atual.name
          metrics = [for nome, funcao in var.funcoes : ["AWS/Lambda", "Invocations", "FunctionName", funcao, { label = nome }]]
          stat    = "Sum"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Erros por função"
          region  = data.aws_region.atual.name
          metrics = [for nome, funcao in var.funcoes : ["AWS/Lambda", "Errors", "FunctionName", funcao, { label = nome }]]
          stat    = "Sum"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title   = "Duração por função (p99)"
          region  = data.aws_region.atual.name
          metrics = [for nome, funcao in var.funcoes : ["AWS/Lambda", "Duration", "FunctionName", funcao, { label = nome, stat = "p99" }]]
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title   = "Mensagens nas DLQs"
          region  = data.aws_region.atual.name
          metrics = [for nome, fila in var.dlqs : ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", fila, { label = nome }]]
          stat    = "Maximum"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "DynamoDB — capacidade consumida"
          region = data.aws_region.atual.name
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.nome_tabela_avaliacoes],
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.nome_tabela_avaliacoes],
          ]
          stat   = "Sum"
          period = 300
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "API Gateway — requisições e erros"
          region = data.aws_region.atual.name
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiId", var.id_api],
            ["AWS/ApiGateway", "4xx", "ApiId", var.id_api],
            ["AWS/ApiGateway", "5xx", "ApiId", var.id_api],
          ]
          stat   = "Sum"
          period = 300
        }
      },
    ]
  })
}
