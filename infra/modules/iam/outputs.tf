output "arn_role_receber_avaliacao" {
  value = aws_iam_role.receber_avaliacao.arn
}

output "arn_role_notificar_urgencia" {
  value = aws_iam_role.notificar_urgencia.arn
}

output "arn_role_gerar_relatorio_semanal" {
  value = aws_iam_role.gerar_relatorio_semanal.arn
}
