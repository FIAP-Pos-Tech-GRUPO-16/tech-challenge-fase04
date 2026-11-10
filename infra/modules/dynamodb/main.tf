resource "aws_dynamodb_table" "avaliacoes" {
  name         = "${var.prefixo}-avaliacoes"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "tipo"
    type = "S"
  }

  attribute {
    name = "dataEnvio"
    type = "S"
  }

  # Partição constante ("AVALIACAO") + ordenação por data: permite consultar o período
  # do relatório semanal com Query, sem Scan na tabela inteira.
  global_secondary_index {
    name            = "gsi_data_envio"
    hash_key        = "tipo"
    range_key       = "dataEnvio"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = {
    Projeto = var.prefixo
  }
}
