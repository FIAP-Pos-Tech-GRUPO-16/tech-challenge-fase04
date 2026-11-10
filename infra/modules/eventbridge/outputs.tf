output "nome_barramento" {
  value = aws_cloudwatch_event_bus.barramento.name
}

output "arn_barramento" {
  value = aws_cloudwatch_event_bus.barramento.arn
}

output "arn_dlq_notificar_urgencia" {
  value = aws_sqs_queue.dlq_notificar_urgencia.arn
}

output "nome_dlq_notificar_urgencia" {
  value = aws_sqs_queue.dlq_notificar_urgencia.name
}

output "arn_dlq_relatorio_semanal" {
  value = aws_sqs_queue.dlq_relatorio_semanal.arn
}

output "nome_dlq_relatorio_semanal" {
  value = aws_sqs_queue.dlq_relatorio_semanal.name
}
