terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Mesmo bucket/lock do state principal (criados em infra/bootstrap/), mas com chave própria:
  # o pipeline fica num state separado para que o deploy da aplicação nunca altere a si mesmo.
  backend "s3" {}
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
