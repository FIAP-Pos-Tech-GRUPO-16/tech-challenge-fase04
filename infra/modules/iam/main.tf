data "aws_caller_identity" "atual" {}
data "aws_region" "atual" {}

locals {
  arn_barramento_eventos = "arn:aws:events:${data.aws_region.atual.name}:${data.aws_caller_identity.atual.account_id}:event-bus/${var.nome_barramento}"
}

# ---------------------------------------------------------------------------
# receber-avaliacao: grava no DynamoDB e publica no EventBridge
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "assume_lambda" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "receber_avaliacao" {
  name               = "${var.prefixo}-receber-avaliacao"
  assume_role_policy = data.aws_iam_policy_document.assume_lambda.json
}

resource "aws_iam_role_policy_attachment" "receber_avaliacao_logs" {
  role       = aws_iam_role.receber_avaliacao.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "receber_avaliacao" {
  statement {
    sid       = "GravarAvaliacao"
    actions   = ["dynamodb:PutItem"]
    resources = [var.arn_tabela_avaliacoes]
  }

  statement {
    sid       = "PublicarEventoAvaliacaoRecebida"
    actions   = ["events:PutEvents"]
    resources = [local.arn_barramento_eventos]
  }
}

resource "aws_iam_role_policy" "receber_avaliacao" {
  name   = "acesso-minimo"
  role   = aws_iam_role.receber_avaliacao.id
  policy = data.aws_iam_policy_document.receber_avaliacao.json
}

# ---------------------------------------------------------------------------
# notificar-urgencia: lê o e-mail do admin no SSM e envia via SES
# ---------------------------------------------------------------------------

resource "aws_iam_role" "notificar_urgencia" {
  name               = "${var.prefixo}-notificar-urgencia"
  assume_role_policy = data.aws_iam_policy_document.assume_lambda.json
}

resource "aws_iam_role_policy_attachment" "notificar_urgencia_logs" {
  role       = aws_iam_role.notificar_urgencia.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "notificar_urgencia" {
  statement {
    sid       = "EnviarAlertaPorEmail"
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = [var.arn_identidade_ses_remetente]
  }

  statement {
    sid       = "LerEmailAdmin"
    actions   = ["ssm:GetParameter"]
    resources = [var.arn_parametro_email_admin]
  }
}

resource "aws_iam_role_policy" "notificar_urgencia" {
  name   = "acesso-minimo"
  role   = aws_iam_role.notificar_urgencia.id
  policy = data.aws_iam_policy_document.notificar_urgencia.json
}

# ---------------------------------------------------------------------------
# gerar-relatorio-semanal: consulta o DynamoDB por período e envia via SES
# ---------------------------------------------------------------------------

resource "aws_iam_role" "gerar_relatorio_semanal" {
  name               = "${var.prefixo}-gerar-relatorio-semanal"
  assume_role_policy = data.aws_iam_policy_document.assume_lambda.json
}

resource "aws_iam_role_policy_attachment" "gerar_relatorio_semanal_logs" {
  role       = aws_iam_role.gerar_relatorio_semanal.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "gerar_relatorio_semanal" {
  statement {
    sid     = "ConsultarAvaliacoesPorPeriodo"
    actions = ["dynamodb:Query"]
    resources = [
      var.arn_tabela_avaliacoes,
      "${var.arn_tabela_avaliacoes}/index/*",
    ]
  }

  statement {
    sid       = "EnviarRelatorioPorEmail"
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = [var.arn_identidade_ses_remetente]
  }

  statement {
    sid       = "LerEmailAdmin"
    actions   = ["ssm:GetParameter"]
    resources = [var.arn_parametro_email_admin]
  }
}

resource "aws_iam_role_policy" "gerar_relatorio_semanal" {
  name   = "acesso-minimo"
  role   = aws_iam_role.gerar_relatorio_semanal.id
  policy = data.aws_iam_policy_document.gerar_relatorio_semanal.json
}

# ---------------------------------------------------------------------------
# Deploy via GitHub Actions (OIDC, sem access keys de longa duração)
# ---------------------------------------------------------------------------

data "tls_certificate" "github_oidc" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_oidc.certificates[0].sha1_fingerprint]
}

data "aws_iam_policy_document" "assume_github_oidc" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.repositorio_github}:*"]
    }
  }
}

resource "aws_iam_role" "deploy_github_actions" {
  name               = "${var.prefixo}-deploy-github-actions"
  assume_role_policy = data.aws_iam_policy_document.assume_github_oidc.json
}

# Permissão ampla para gerenciar os recursos deste projeto via Terraform. Escopo por
# prefixo de nome onde o tipo de recurso permite; para os serviços que exigem permissão
# de leitura geral (ex.: IAM) o acesso é de leitura/gestão restrita às roles do projeto.
data "aws_iam_policy_document" "deploy_github_actions" {
  statement {
    sid = "GerenciarRecursosDoProjeto"
    actions = [
      "lambda:*",
      "apigateway:*",
      "events:*",
      "scheduler:*",
      "dynamodb:*",
      "ses:*",
      "sesv2:*",
      "ssm:*",
      "sqs:*",
      "cloudwatch:*",
      "logs:*",
      "sns:*",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "GerenciarRolesDoProjeto"
    actions   = ["iam:*"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.atual.account_id}:role/${var.prefixo}-*"]
  }

  statement {
    sid       = "AcessoAoStateRemoto"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:ListBucket"]
    resources = [
      "arn:aws:s3:::${var.prefixo}-terraform-state",
      "arn:aws:s3:::${var.prefixo}-terraform-state/*",
    ]
  }

  statement {
    sid       = "LockDoState"
    actions   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem"]
    resources = ["arn:aws:dynamodb:${data.aws_region.atual.name}:${data.aws_caller_identity.atual.account_id}:table/${var.prefixo}-terraform-lock"]
  }
}

resource "aws_iam_role_policy" "deploy_github_actions" {
  name   = "deploy-terraform"
  role   = aws_iam_role.deploy_github_actions.id
  policy = data.aws_iam_policy_document.deploy_github_actions.json
}
