# ---------------------------------------------------------------------------
# Deploy contínuo dentro da própria conta AWS.
#
# O caminho "natural" (GitHub Actions assumindo uma role via OIDC) é inviável aqui: a SCP da
# nova experiência AWS nega iam:CreateOpenIDConnectProvider. A alternativa sem segredo de
# longa duração é inverter o sentido: em vez do GitHub entrar na AWS, a AWS lê o GitHub, via
# CodeConnections (GitHub App), e roda o deploy no CodeBuild com uma role da própria conta.
#
#   push na main ──► CodePipeline (V2) ──► CodeBuild: testes unitários + mvn package
#                                                      + terraform plan/apply de infra/
# ---------------------------------------------------------------------------

data "aws_caller_identity" "atual" {}
data "aws_region" "atual" {}

locals {
  conta  = data.aws_caller_identity.atual.account_id
  regiao = data.aws_region.atual.name

  # Roles do CI/CD propositalmente FORA do padrão "<prefixo>-*": a role de deploy pode gerenciar
  # as roles da aplicação (<prefixo>-*), mas não consegue alterar a si mesma.
  nome_role_codebuild    = "cicd-${var.prefixo}-codebuild-deploy"
  nome_role_codepipeline = "cicd-${var.prefixo}-codepipeline"
  nome_projeto_codebuild = "${var.prefixo}-deploy"
}

# A conexão nasce PENDING: a AWS exige que alguém com acesso à organização do GitHub instale o
# "AWS Connector for GitHub" e aprove a conexão no console uma única vez (não automatizável).
resource "aws_codestarconnections_connection" "github" {
  name          = "${var.prefixo}-github"
  provider_type = "GitHub"
}

# ---------------------------------------------------------------------------
# Bucket de artefatos do pipeline (código-fonte zipado repassado ao CodeBuild)
# ---------------------------------------------------------------------------

resource "aws_s3_bucket" "artefatos" {
  bucket        = "${var.prefixo}-pipeline-artefatos-${local.conta}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "artefatos" {
  bucket                  = aws_s3_bucket.artefatos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  rule {
    id     = "expirar-artefatos-antigos"
    status = "Enabled"

    filter {}

    expiration {
      days = 30
    }
  }
}

# ---------------------------------------------------------------------------
# CodeBuild: build + terraform apply
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "codebuild" {
  name              = "/aws/codebuild/${local.nome_projeto_codebuild}"
  retention_in_days = 30
}

data "aws_iam_policy_document" "assume_codebuild" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["codebuild.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.conta]
    }
  }
}

resource "aws_iam_role" "codebuild" {
  name               = local.nome_role_codebuild
  assume_role_policy = data.aws_iam_policy_document.assume_codebuild.json
}

data "aws_iam_policy_document" "codebuild" {
  # Serviços que o Terraform da aplicação gerencia. Resource "*" porque o provider também faz
  # chamadas de leitura/listagem que não aceitam ARN específico; a conta é dedicada ao projeto.
  statement {
    sid = "GerenciarServicosDaAplicacao"
    actions = [
      "apigateway:*",
      "cloudwatch:*",
      "cognito-idp:*",
      "dynamodb:*",
      "events:*",
      "lambda:*",
      "logs:*",
      "scheduler:*",
      "ses:*",
      "sns:*",
      "sqs:*",
      "ssm:*",
    ]
    resources = ["*"]
  }

  # IAM é o ponto sensível (escalada de privilégio): só as roles da aplicação, só a policy
  # gerenciada de logs pode ser anexada e PassRole só para Lambda e Scheduler.
  statement {
    sid = "GerenciarRolesDaAplicacao"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:ListRoleTags",
      "iam:PutRolePolicy",
      "iam:GetRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
    ]
    resources = ["arn:aws:iam::${local.conta}:role/${var.prefixo}-*"]
  }

  statement {
    sid       = "AnexarSomentePolicyDeLogsDoLambda"
    actions   = ["iam:AttachRolePolicy", "iam:DetachRolePolicy"]
    resources = ["arn:aws:iam::${local.conta}:role/${var.prefixo}-*"]
    condition {
      test     = "ArnEquals"
      variable = "iam:PolicyARN"
      values   = ["arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"]
    }
  }

  statement {
    sid       = "RepassarRolesDaAplicacao"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${local.conta}:role/${var.prefixo}-*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["lambda.amazonaws.com", "scheduler.amazonaws.com"]
    }
  }

  statement {
    sid       = "StateRemotoDoTerraform"
    actions   = ["s3:ListBucket", "s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::${var.bucket_state_terraform}", "arn:aws:s3:::${var.bucket_state_terraform}/infra/*"]
  }

  statement {
    sid       = "LerArtefatosDoPipeline"
    actions   = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:GetBucketLocation"]
    resources = [aws_s3_bucket.artefatos.arn, "${aws_s3_bucket.artefatos.arn}/*"]
  }
}

resource "aws_iam_role_policy" "codebuild" {
  name   = "deploy-da-aplicacao"
  role   = aws_iam_role.codebuild.id
  policy = data.aws_iam_policy_document.codebuild.json
}

resource "aws_codebuild_project" "deploy" {
  name          = local.nome_projeto_codebuild
  description   = "Roda os testes unitários, empacota as 3 funções e aplica o Terraform de infra/"
  service_role  = aws_iam_role.codebuild.arn
  build_timeout = 60

  source {
    type      = "CODEPIPELINE"
    buildspec = "infra/cicd/buildspec.yml"
  }

  artifacts {
    type = "CODEPIPELINE"
  }

  environment {
    type         = "LINUX_CONTAINER"
    compute_type = "BUILD_GENERAL1_SMALL"
    image        = "aws/codebuild/amazonlinux-x86_64-standard:5.0"

    environment_variable {
      name  = "TF_VAR_email_admin_inicial"
      value = var.email_admin_inicial
    }

    environment_variable {
      name  = "TF_VAR_email_remetente"
      value = var.email_remetente
    }
  }

  cache {
    type  = "LOCAL"
    modes = ["LOCAL_CUSTOM_CACHE"]
  }

  logs_config {
    cloudwatch_logs {
      group_name = aws_cloudwatch_log_group.codebuild.name
    }
  }
}

# ---------------------------------------------------------------------------
# CodePipeline V2: dispara a cada push na main que mexa em código ou infraestrutura
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "assume_codepipeline" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["codepipeline.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.conta]
    }
  }
}

resource "aws_iam_role" "codepipeline" {
  name               = local.nome_role_codepipeline
  assume_role_policy = data.aws_iam_policy_document.assume_codepipeline.json
}

data "aws_iam_policy_document" "codepipeline" {
  statement {
    sid       = "ArtefatosDoPipeline"
    actions   = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:GetBucketVersioning", "s3:GetBucketLocation"]
    resources = [aws_s3_bucket.artefatos.arn, "${aws_s3_bucket.artefatos.arn}/*"]
  }

  statement {
    sid       = "LerCodigoDoGitHub"
    actions   = ["codestar-connections:UseConnection", "codeconnections:UseConnection"]
    resources = [aws_codestarconnections_connection.github.arn]
  }

  statement {
    sid       = "RodarDeployNoCodeBuild"
    actions   = ["codebuild:StartBuild", "codebuild:BatchGetBuilds"]
    resources = [aws_codebuild_project.deploy.arn]
  }
}

resource "aws_iam_role_policy" "codepipeline" {
  name   = "orquestrar-deploy"
  role   = aws_iam_role.codepipeline.id
  policy = data.aws_iam_policy_document.codepipeline.json
}

resource "aws_codepipeline" "deploy" {
  name          = "${var.prefixo}-deploy"
  role_arn      = aws_iam_role.codepipeline.arn
  pipeline_type = "V2"

  # Execuções enfileiradas: dois applies do Terraform nunca rodam ao mesmo tempo.
  execution_mode = "QUEUED"

  artifact_store {
    type     = "S3"
    location = aws_s3_bucket.artefatos.bucket
  }

  stage {
    name = "Codigo"

    action {
      name             = "GitHub"
      category         = "Source"
      owner            = "AWS"
      provider         = "CodeStarSourceConnection"
      version          = "1"
      output_artifacts = ["codigo"]

      configuration = {
        ConnectionArn    = aws_codestarconnections_connection.github.arn
        FullRepositoryId = var.repositorio_github
        BranchName       = var.branch
        # Precisa ser "true": com "false" o CodePipeline ignora os commits novos, inclusive os que
        # casam com o trigger abaixo (que só refina quais pushes disparam a execução).
        DetectChanges = "true"
      }
    }
  }

  stage {
    name = "BuildEDeploy"

    action {
      name            = "TestesBuildTerraformApply"
      category        = "Build"
      owner           = "AWS"
      provider        = "CodeBuild"
      version         = "1"
      input_artifacts = ["codigo"]

      configuration = {
        ProjectName = aws_codebuild_project.deploy.name
      }
    }
  }

  # Mudanças só em documentação (docs/, README, postman/) não disparam deploy.
  trigger {
    provider_type = "CodeStarSourceConnection"

    git_configuration {
      source_action_name = "GitHub"

      push {
        branches {
          includes = [var.branch]
        }

        file_paths {
          includes = [
            "pom.xml",
            "modulo-*/**",
            "funcao-*/**",
            "infra/**",
          ]
        }
      }
    }
  }
}
