terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    # Valores reais (bucket, key, region, dynamodb_table) vêm de infra/backend.hcl,
    # passados via `terraform init -backend-config=backend.hcl`. Veja infra/bootstrap/
    # para a criação do bucket e da tabela de lock.
  }
}

provider "aws" {
  region = var.regiao

  default_tags {
    tags = {
      Projeto       = "tech-challenge-fase04"
      GerenciadoPor = "terraform"
    }
  }
}
