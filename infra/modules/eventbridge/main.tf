data "aws_caller_identity" "atual" {}
data "aws_region" "atual" {}

resource "aws_cloudwatch_event_bus" "barramento" {
  name = var.nome_barramento
}

# ---------------------------------------------------------------------------
# DLQs — todo alvo assíncrono do EventBridge (regra ou scheduler) tem uma fila de
# mensagens mortas: se a notificação de urgência ou o relatório semanal falharem
# mesmo após retry, a mensagem fica visível aqui em vez de se perder silenciosamente.
# ---------------------------------------------------------------------------

resource "aws_sqs_queue" "dlq_notificar_urgencia" {
  name                      = "${var.prefixo}-dlq-notificar-urgencia"
  message_retention_seconds = 1209600 # 14 dias
  sqs_managed_sse_enabled   = true

  tags = { Projeto = var.prefixo }
}

resource "aws_sqs_queue" "dlq_relatorio_semanal" {
  name                      = "${var.prefixo}-dlq-relatorio-semanal"
  message_retention_seconds = 1209600 # 14 dias
  sqs_managed_sse_enabled   = true

  tags = { Projeto = var.prefixo }
}

data "aws_iam_policy_document" "dlq_notificar_urgencia" {
  statement {
    sid       = "PermitirEventBridgeEnviarParaDLQ"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq_notificar_urgencia.arn]

    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.regra_urgencia_alta.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "dlq_notificar_urgencia" {
  queue_url = aws_sqs_queue.dlq_notificar_urgencia.id
  policy    = data.aws_iam_policy_document.dlq_notificar_urgencia.json
}

data "aws_iam_policy_document" "dlq_relatorio_semanal" {
  statement {
    sid       = "PermitirSchedulerEnviarParaDLQ"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq_relatorio_semanal.arn]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_scheduler_schedule.relatorio_semanal.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "dlq_relatorio_semanal" {
  queue_url = aws_sqs_queue.dlq_relatorio_semanal.id
  policy    = data.aws_iam_policy_document.dlq_relatorio_semanal.json
}

# ---------------------------------------------------------------------------
# Regra: avaliação recebida com urgência ALTA -> notificar-urgencia
# A classificação em si é regra de domínio (calculada no código); esta regra só roteia
# com base no campo nivelUrgencia que já vem calculado no evento.
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_event_rule" "regra_urgencia_alta" {
  name           = "${var.prefixo}-urgencia-alta"
  event_bus_name = aws_cloudwatch_event_bus.barramento.name

  event_pattern = jsonencode({
    detail-type = ["AvaliacaoRecebida"]
    detail = {
      nivelUrgencia = ["ALTA"]
    }
  })
}

resource "aws_cloudwatch_event_target" "notificar_urgencia" {
  rule           = aws_cloudwatch_event_rule.regra_urgencia_alta.name
  event_bus_name = aws_cloudwatch_event_bus.barramento.name
  arn            = var.arn_funcao_notificar_urgencia

  retry_policy {
    maximum_retry_attempts       = 2
    maximum_event_age_in_seconds = 3600
  }

  dead_letter_config {
    arn = aws_sqs_queue.dlq_notificar_urgencia.arn
  }
}

resource "aws_lambda_permission" "permitir_eventbridge_notificar_urgencia" {
  statement_id  = "PermitirEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = var.nome_funcao_notificar_urgencia
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.regra_urgencia_alta.arn
}

# ---------------------------------------------------------------------------
# Agendamento semanal: EventBridge Scheduler -> gerar-relatorio-semanal
# Segunda-feira 08:00 BRT (UTC-3, sem horário de verão) = 11:00 UTC.
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "assume_scheduler" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler_relatorio_semanal" {
  name               = "${var.prefixo}-scheduler-relatorio-semanal"
  assume_role_policy = data.aws_iam_policy_document.assume_scheduler.json
}

data "aws_iam_policy_document" "scheduler_relatorio_semanal" {
  statement {
    sid       = "InvocarFuncaoDeRelatorio"
    actions   = ["lambda:InvokeFunction"]
    resources = [var.arn_funcao_gerar_relatorio_semanal]
  }
}

resource "aws_iam_role_policy" "scheduler_relatorio_semanal" {
  name   = "invocar-funcao"
  role   = aws_iam_role.scheduler_relatorio_semanal.id
  policy = data.aws_iam_policy_document.scheduler_relatorio_semanal.json
}

resource "aws_scheduler_schedule" "relatorio_semanal" {
  name = "${var.prefixo}-relatorio-semanal"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = "cron(0 11 ? * MON *)"
  schedule_expression_timezone = "UTC"

  target {
    arn      = var.arn_funcao_gerar_relatorio_semanal
    role_arn = aws_iam_role.scheduler_relatorio_semanal.arn

    retry_policy {
      maximum_retry_attempts = 2
    }

    dead_letter_config {
      arn = aws_sqs_queue.dlq_relatorio_semanal.arn
    }
  }
}

resource "aws_lambda_permission" "permitir_scheduler_relatorio_semanal" {
  statement_id  = "PermitirEventBridgeScheduler"
  action        = "lambda:InvokeFunction"
  function_name = var.nome_funcao_gerar_relatorio_semanal
  principal     = "scheduler.amazonaws.com"
  source_arn    = aws_scheduler_schedule.relatorio_semanal.arn
}
