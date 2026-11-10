resource "aws_ssm_parameter" "email_admin" {
  name        = "/${var.prefixo}/email-admin"
  description = "E-mail do administrador que recebe alertas de urgência e o relatório semanal"
  type        = "String"
  value       = var.email_admin_inicial

  tags = {
    Projeto = var.prefixo
  }

  # O valor muda sem passar pelo Terraform (ex.: outro integrante assume o e-mail de admin
  # antes de gravar o vídeo) — não queremos que um futuro `apply` reverta essa troca manual.
  lifecycle {
    ignore_changes = [value]
  }
}
