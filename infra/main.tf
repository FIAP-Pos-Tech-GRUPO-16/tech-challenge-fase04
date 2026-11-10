locals {
  nome_barramento = "${var.prefixo}-feedback"

  caminho_jar_receber_avaliacao       = "${path.module}/../funcao-receber-avaliacao/target/funcao-receber-avaliacao.jar"
  caminho_jar_notificar_urgencia      = "${path.module}/../funcao-notificar-urgencia/target/funcao-notificar-urgencia.jar"
  caminho_jar_gerar_relatorio_semanal = "${path.module}/../funcao-gerar-relatorio-semanal/target/funcao-gerar-relatorio-semanal.jar"
}

module "dynamodb" {
  source  = "./modules/dynamodb"
  prefixo = var.prefixo
}

module "ssm" {
  source              = "./modules/ssm"
  prefixo             = var.prefixo
  email_admin_inicial = var.email_admin_inicial
}

module "ses" {
  source              = "./modules/ses"
  prefixo             = var.prefixo
  email_remetente     = var.email_remetente
  email_admin_inicial = var.email_admin_inicial
}

module "iam" {
  source                       = "./modules/iam"
  prefixo                      = var.prefixo
  arn_tabela_avaliacoes        = module.dynamodb.arn_tabela
  arn_identidade_ses_remetente = module.ses.arn_identidade_remetente
  arn_parametro_email_admin    = module.ssm.arn_parametro
  nome_barramento              = local.nome_barramento
}

module "funcao_receber_avaliacao" {
  source             = "./modules/lambda"
  prefixo            = var.prefixo
  nome_funcao        = "${var.prefixo}-receber-avaliacao"
  descricao          = "Valida, persiste e publica o evento de uma avaliação recebida"
  arn_role           = module.iam.arn_role_receber_avaliacao
  caminho_jar        = local.caminho_jar_receber_avaliacao
  classe_principal   = "br.com.fiap.techchallenge.feedback.funcao.receberavaliacao.AplicacaoReceberAvaliacao"
  memoria_mb         = var.memoria_funcao_mb
  timeout_segundos   = var.timeout_funcao_segundos
  retencao_logs_dias = var.retencao_logs_dias

  variaveis_ambiente = {
    TABELA_AVALIACOES      = module.dynamodb.nome_tabela
    EVENTBRIDGE_BARRAMENTO = local.nome_barramento
  }
}

module "funcao_notificar_urgencia" {
  source             = "./modules/lambda"
  prefixo            = var.prefixo
  nome_funcao        = "${var.prefixo}-notificar-urgencia"
  descricao          = "Envia alerta por e-mail quando uma avaliação crítica é recebida"
  arn_role           = module.iam.arn_role_notificar_urgencia
  caminho_jar        = local.caminho_jar_notificar_urgencia
  classe_principal   = "br.com.fiap.techchallenge.feedback.funcao.notificarurgencia.AplicacaoNotificarUrgencia"
  memoria_mb         = var.memoria_funcao_mb
  timeout_segundos   = var.timeout_funcao_segundos
  retencao_logs_dias = var.retencao_logs_dias

  variaveis_ambiente = {
    SES_REMETENTE             = var.email_remetente
    SSM_PARAMETRO_EMAIL_ADMIN = module.ssm.nome_parametro
  }
}

module "funcao_gerar_relatorio_semanal" {
  source             = "./modules/lambda"
  prefixo            = var.prefixo
  nome_funcao        = "${var.prefixo}-gerar-relatorio-semanal"
  descricao          = "Agrega as avaliações da semana e envia o relatório por e-mail"
  arn_role           = module.iam.arn_role_gerar_relatorio_semanal
  caminho_jar        = local.caminho_jar_gerar_relatorio_semanal
  classe_principal   = "br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal.AplicacaoGerarRelatorioSemanal"
  memoria_mb         = var.memoria_funcao_mb
  timeout_segundos   = var.timeout_funcao_segundos
  retencao_logs_dias = var.retencao_logs_dias

  variaveis_ambiente = {
    TABELA_AVALIACOES         = module.dynamodb.nome_tabela
    SES_REMETENTE             = var.email_remetente
    SSM_PARAMETRO_EMAIL_ADMIN = module.ssm.nome_parametro
  }
}

module "eventbridge" {
  source          = "./modules/eventbridge"
  prefixo         = var.prefixo
  nome_barramento = local.nome_barramento

  # Alias "live" (aponta para a versão publicada mais recente) — o SnapStart só se aplica a
  # versões publicadas, nunca a $LATEST, então os alvos apontam para o alias, não para a função base.
  arn_funcao_notificar_urgencia        = module.funcao_notificar_urgencia.arn_alias
  nome_funcao_notificar_urgencia       = module.funcao_notificar_urgencia.nome_funcao
  alias_funcao_notificar_urgencia      = module.funcao_notificar_urgencia.nome_alias
  arn_funcao_gerar_relatorio_semanal   = module.funcao_gerar_relatorio_semanal.arn_alias
  nome_funcao_gerar_relatorio_semanal  = module.funcao_gerar_relatorio_semanal.nome_funcao
  alias_funcao_gerar_relatorio_semanal = module.funcao_gerar_relatorio_semanal.nome_alias
}

module "api_gateway" {
  source  = "./modules/api-gateway"
  prefixo = var.prefixo

  # Idem: aponta para o alias "live", não para a função base — necessário para o SnapStart.
  arn_invocacao_funcao_receber_avaliacao = module.funcao_receber_avaliacao.arn_invocacao_alias
  nome_funcao_receber_avaliacao          = module.funcao_receber_avaliacao.nome_funcao
  alias_funcao_receber_avaliacao         = module.funcao_receber_avaliacao.nome_alias
  retencao_logs_dias                     = var.retencao_logs_dias
}

module "cloudwatch" {
  source                  = "./modules/cloudwatch"
  prefixo                 = var.prefixo
  email_admin_inicial     = var.email_admin_inicial
  timeout_funcao_segundos = var.timeout_funcao_segundos
  nome_tabela_avaliacoes  = module.dynamodb.nome_tabela
  id_api                  = module.api_gateway.id_api

  funcoes = {
    receber-avaliacao       = module.funcao_receber_avaliacao.nome_funcao
    notificar-urgencia      = module.funcao_notificar_urgencia.nome_funcao
    gerar-relatorio-semanal = module.funcao_gerar_relatorio_semanal.nome_funcao
  }

  dlqs = {
    notificar-urgencia      = module.eventbridge.nome_dlq_notificar_urgencia
    gerar-relatorio-semanal = module.eventbridge.nome_dlq_relatorio_semanal
  }
}
