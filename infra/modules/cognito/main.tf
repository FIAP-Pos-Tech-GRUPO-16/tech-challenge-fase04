data "aws_region" "atual" {}

# User Pool para autenticação de estudantes no endpoint público POST /avaliacao.
#
# Decisão: sem fluxo de auto-cadastro com verificação de e-mail (adiciona complexidade de teste
# sem valor para este desafio — o enunciado não pede cadastro de usuários). Usuários de teste são
# criados via admin (AdminCreateUser + AdminSetUserPassword, documentado em docs/deploy.md e na
# collection do Postman) e autenticam com usuário/senha (fluxo USER_PASSWORD_AUTH) para obter um
# JWT, validado pelo API Gateway antes de invocar a função — a Lambda não sabe nada sobre auth.

resource "aws_cognito_user_pool" "estudantes" {
  name = "${var.prefixo}-estudantes"

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  tags = {
    Projeto = var.prefixo
  }
}

resource "aws_cognito_user_pool_client" "api_estudantes" {
  name         = "${var.prefixo}-api-estudantes"
  user_pool_id = aws_cognito_user_pool.estudantes.id

  # Cliente público (sem secret) — necessário para chamar InitiateAuth diretamente (CLI/Postman)
  # sem precisar calcular o SECRET_HASH.
  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_ADMIN_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 1

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}
