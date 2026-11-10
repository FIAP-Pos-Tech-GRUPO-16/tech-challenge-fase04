output "url_dashboard" {
  value = "https://${data.aws_region.atual.name}.console.aws.amazon.com/cloudwatch/home?region=${data.aws_region.atual.name}#dashboards:name=${aws_cloudwatch_dashboard.principal.dashboard_name}"
}

output "arn_topico_alertas" {
  value = aws_sns_topic.alertas_operacionais.arn
}
