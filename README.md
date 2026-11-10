# Plataforma de Feedback de Aulas — Tech Challenge Fase 4

Tech Challenge da Fase 4 da Pós-Graduação FIAP em Arquitetura e Desenvolvimento Java (Grupo 16).

Plataforma serverless para recebimento de avaliações de aulas, notificação automática de
administradores em casos críticos e geração de relatório semanal consolidado — 100% AWS,
Clean Architecture, deploy automatizado.

Enunciado completo em [`docs/ADJT-Fase4-Tech-Challenge.pdf`](docs/ADJT-Fase4-Tech-Challenge.pdf).

## Documentação

- [Arquitetura da solução](docs/arquitetura.md) — modelo de cloud, componentes, decisões de design
- [Instruções de deploy](docs/deploy.md) — setup da conta AWS, bootstrap do Terraform, pipeline
- [Configuração do monitoramento](docs/monitoramento.md) — logs, dashboard, alarmes
- [Documentação das funções](docs/funcoes.md) — gatilho, entrada/saída e IAM de cada Lambda
- [Roteiro do vídeo de demonstração](docs/video.md)

## Estrutura do repositório

```
tech-challenge-fase04/
├── pom.xml                        (parent Maven)
├── modulo-dominio/                (entidades + regras puras — zero dependência de framework/AWS)
├── modulo-aplicacao/              (casos de uso + portas/interfaces)
├── modulo-infraestrutura/         (adapters: DynamoDB, SES, EventBridge via AWS SDK v2)
├── funcao-receber-avaliacao/      (Lambda: POST /avaliacao)
├── funcao-notificar-urgencia/     (Lambda: alerta de urgência via EventBridge)
├── funcao-gerar-relatorio-semanal/ (Lambda: relatório semanal via EventBridge Scheduler)
├── infra/                         (Terraform: dynamodb, lambda, api-gateway, eventbridge, ses,
│                                    iam, ssm, cloudwatch)
├── .github/workflows/             (ci.yml, deploy.yml)
└── docs/
```

## Build e testes

```
mvn test                              # unitários (domínio, aplicação, adapters, handlers)
mvn -pl modulo-infraestrutura verify   # + integração contra LocalStack (DynamoDB, EventBridge)
mvn -DskipTests package                # empacota os jars das 3 funções Lambda
```

Testes de integração exigem Docker e a variável `LOCALSTACK_AUTH_TOKEN` (conta gratuita em
app.localstack.cloud — ver [`deploy.md`](docs/deploy.md)).

## Stack

Java 21 · Spring Cloud Function · AWS Lambda · API Gateway · DynamoDB · EventBridge · SES ·
SSM Parameter Store · Terraform · GitHub Actions.
