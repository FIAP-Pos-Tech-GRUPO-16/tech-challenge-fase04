# Em modo sandbox (padrão de uma conta AWS nova), SES exige que remetente E destinatário
# estejam verificados individualmente. O Terraform cria as identidades e dispara o e-mail
# de verificação da AWS — confirmar o link recebido em cada caixa de entrada é um passo
# manual, fora do Terraform (documentado em docs/deploy.md).

resource "aws_sesv2_email_identity" "remetente" {
  email_identity = var.email_remetente

  tags = {
    Projeto = var.prefixo
  }
}

resource "aws_sesv2_email_identity" "destinatario_inicial" {
  # Evita tentar criar a mesma identidade duas vezes quando remetente e destinatário são o
  # mesmo e-mail (cenário comum para este desafio).
  count = var.email_admin_inicial == var.email_remetente ? 0 : 1

  email_identity = var.email_admin_inicial

  tags = {
    Projeto = var.prefixo
  }
}
