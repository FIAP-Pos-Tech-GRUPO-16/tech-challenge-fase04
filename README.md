# Plataforma de Feedback de Aulas

**Tech Challenge — Fase 4** · Pós-Graduação FIAP em Arquitetura e Desenvolvimento Java · Grupo 16

[![CI](https://github.com/FIAP-Pos-Tech-GRUPO-16/tech-challenge-fase04/actions/workflows/ci.yml/badge.svg)](https://github.com/FIAP-Pos-Tech-GRUPO-16/tech-challenge-fase04/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![AWS](https://img.shields.io/badge/AWS-Lambda%20%7C%20DynamoDB%20%7C%20EventBridge-%23FF9900)
![Terraform](https://img.shields.io/badge/IaC-Terraform-7B42BC)

Plataforma **100% serverless**, rodando de verdade na AWS, para estudantes avaliarem aulas e
administradores acompanharem a satisfação da turma: notificação automática de feedbacks críticos
e relatório semanal consolidado por e-mail.

> 🚀 **API já implantada e funcionando**: `https://0wywnn2xkg.execute-api.us-east-2.amazonaws.com`
> (região `us-east-2`). Teste direto com a [collection do Postman](postman/) ou com `curl` — ver
> seção [Como testar](#como-testar).

---

## Índice

- [O problema e o objetivo](#o-problema-e-o-objetivo)
- [Arquitetura](#arquitetura)
- [Tecnologias](#tecnologias)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Como funciona](#como-funciona)
- [Como executar e testar localmente](#como-executar-e-testar-localmente)
- [Como testar](#como-testar)
- [Deploy](#deploy)
- [Monitoramento](#monitoramento)
- [Decisões de arquitetura — por quê](#decisões-de-arquitetura--por-quê)
- [Documentação completa](#documentação-completa)
- [Integrantes do grupo](#integrantes-do-grupo)

---

## O problema e o objetivo

Enunciado completo em [`docs/ADJT-Fase4-Tech-Challenge.pdf`](docs/ADJT-Fase4-Tech-Challenge.pdf).

Para garantir a qualidade dos cursos online, estudantes precisam poder avaliar as aulas, e
administradores precisam acompanhar rapidamente a satisfação da turma. O sistema precisa:

- Receber avaliações (`descrição` + `nota` de 0 a 10).
- Notificar administradores automaticamente quando um feedback for crítico.
- Gerar um relatório semanal com média de avaliações.
- Rodar em ambiente cloud, com **no mínimo 2 funções serverless** de responsabilidade única,
  deploy automatizado, segurança/governança de acesso e aplicação monitorada.

Este projeto entrega **3 funções serverless** (acima do mínimo exigido), cada uma com uma única
responsabilidade — ver [Como funciona](#como-funciona).

## Arquitetura

```
                         ┌─────────────────────┐
  Estudante  ── POST ──▶ │   API Gateway (HTTP) │
                         └──────────┬───────────┘
                                    ▼
                    ┌───────────────────────────────┐
                    │ Lambda: receber-avaliacao      │  (SRP: validar + persistir + publicar evento)
                    └───────┬───────────────┬────────┘
                            ▼                ▼
                     ┌────────────┐   ┌──────────────────────────┐
                     │ DynamoDB    │   │ EventBridge (bus próprio) │
                     └─────┬──────┘   └──────────┬────────────────┘
                           │                       │ regra: nivelUrgencia = ALTA
             consulta      │                       ▼
             (GSI por data)│          ┌─────────────────────────────┐
                           │          │ Lambda: notificar-urgencia   │ (SRP: só notifica)
                           │          └──────────────┬───────────────┘
                           │                          ▼
                           │                     SES ──▶ e-mail do admin (SSM Parameter)
                    ┌──────▼─────────────────────────┐
                    │ EventBridge Scheduler (semanal) │
                    └──────────────┬──────────────────┘
                                   ▼
                    ┌───────────────────────────────────┐
                    │ Lambda: gerar-relatorio-semanal    │ (SRP: agrega + envia relatório)
                    └──────────────────┬──────────────────┘
                                       ▼
                                  SES ──▶ e-mail do admin (relatório)
```

Detalhamento completo, trade-offs e justificativas de cada escolha em
[`docs/arquitetura.md`](docs/arquitetura.md).

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem / runtime | Java 21, AWS Lambda |
| Framework das funções | Spring Boot 3.5 + Spring Cloud Function 4.3 (adapter AWS) |
| Cloud | AWS — Lambda (SnapStart), API Gateway (HTTP API + JWT Authorizer), Cognito, DynamoDB, EventBridge (bus + regra + Scheduler), SES, SSM Parameter Store, SQS (DLQ), CloudWatch (logs/alarmes/dashboard), SNS |
| AWS SDK | AWS SDK for Java v2 (DynamoDB Enhanced Client, SESv2, EventBridge, SSM) |
| Infraestrutura como código | Terraform 1.17 |
| CI/CD | GitHub Actions |
| Build | Maven (multi-módulo) |
| Testes | JUnit 5, AssertJ, Mockito, Testcontainers + LocalStack |
| Testes de API | Postman (collection em [`postman/`](postman/)) |

## Estrutura do repositório

```
tech-challenge-fase04/
├── pom.xml                           (parent Maven)
├── modulo-dominio/                   (entidades + regras puras — zero dependência de framework/AWS)
├── modulo-aplicacao/                 (casos de uso + portas/interfaces — Clean Architecture)
├── modulo-infraestrutura/            (adapters: DynamoDB, SES, EventBridge via AWS SDK v2)
├── funcao-receber-avaliacao/         (Lambda: POST /avaliacao)
├── funcao-notificar-urgencia/        (Lambda: alerta de urgência via EventBridge)
├── funcao-gerar-relatorio-semanal/   (Lambda: relatório semanal via EventBridge Scheduler)
├── infra/                            (Terraform: toda a infraestrutura AWS)
│   └── bootstrap/                    (bucket S3 + tabela DynamoDB do state remoto)
├── .github/workflows/                (ci.yml, deploy.yml)
├── postman/                          (collection + environment para testar a API real)
└── docs/                             (arquitetura, deploy, monitoramento, funções, vídeo)
```

Dependências entre camadas (Clean Architecture, regra de dependência de dentro para fora):
`modulo-dominio` ← `modulo-aplicacao` ← `modulo-infraestrutura` / `funcao-*`. O domínio nunca
depende de Spring, AWS SDK ou qualquer framework; os casos de uso dependem só de portas
(interfaces) definidas no próprio `modulo-aplicacao`.

## Como funciona

1. O estudante autentica no **Cognito** (usuário/senha) e recebe um JWT. `POST /avaliacao` exige
   `Authorization: Bearer <token>` — o **API Gateway valida o token antes de invocar a Lambda**
   (a função não tem nenhum código de autenticação).
2. **`receber-avaliacao`** recebe `{ "descricao": string, "nota": 0-10 }`, valida, classifica a
   urgência (**regra de domínio**: nota 0-3 = ALTA, 4-6 = MÉDIA, 7-10 = BAIXA), persiste no
   DynamoDB e publica o evento `AvaliacaoRecebida` no EventBridge — já com o nível de urgência
   calculado.
3. Uma regra do EventBridge roteia o evento para **`notificar-urgencia`** só quando
   `nivelUrgencia = ALTA`. A função envia um e-mail de alerta via SES (descrição, urgência, data).
4. Toda segunda-feira (EventBridge Scheduler), **`gerar-relatorio-semanal`** agrega as avaliações
   dos últimos 7 dias (via índice do DynamoDB, sem Scan) e envia por e-mail: lista das avaliações,
   média das notas, quantidade por dia e por nível de urgência.
5. O e-mail do administrador fica no **SSM Parameter Store** (nunca hardcoded) — trocar o
   destinatário não exige novo deploy. Cada alvo assíncrono do EventBridge tem uma **DLQ** própria
   (SQS), então nenhuma falha é descartada silenciosamente.

Documentação completa de cada função (gatilho, entrada/saída, variáveis de ambiente, IAM) em
[`docs/funcoes.md`](docs/funcoes.md).

## Como executar e testar localmente

Pré-requisitos: Java 21, Maven 3.9+, Docker (para os testes de integração contra LocalStack).

```bash
# testes unitários (domínio, aplicação, adapters, handlers das 3 funções)
mvn test

# + testes de integração reais contra LocalStack (DynamoDB, EventBridge)
# exige LOCALSTACK_AUTH_TOKEN (conta gratuita em app.localstack.cloud)
export LOCALSTACK_AUTH_TOKEN=<seu-token>
mvn -pl modulo-infraestrutura -am verify

# empacota as 3 funções em jars sombreados, prontos para deploy
mvn -DskipTests package
```

> SES não tem teste de integração via LocalStack: desde março de 2026 esse serviço saiu da camada
> gratuita do LocalStack. O adapter `EnviadorEmailSes` tem cobertura via teste unitário
> (`SesV2Client` mockado); a validação real do envio acontece contra a AWS de verdade (ver
> [Como testar](#como-testar)).

Rodar uma função Lambda localmente fora do ambiente AWS não é suportado neste projeto (o
`FunctionInvoker` do Spring Cloud Function é feito para rodar dentro do Lambda) — os testes acima
e a collection do Postman contra o deploy real cobrem o comportamento de ponta a ponta.

## Como testar

### Contra o deploy real na AWS (recomendado)

Importe a [collection do Postman](postman/) (`postman/tech-challenge-fase04.postman_collection.json`
+ `postman/tech-challenge-fase04.postman_environment.json`) — já configurada com a URL da API em
produção. Cobre os 3 componentes: autenticação (Cognito — cria usuário de teste e obtém o JWT
automaticamente), o endpoint HTTP e as duas funções orientadas a evento/agendamento (invocadas
diretamente via API de Invoke do Lambda com AWS Signature v4). Instruções detalhadas em
[`postman/README.md`](postman/README.md).

### Com `curl`

`POST /avaliacao` exige um JWT do Cognito (ver [`docs/deploy.md`](docs/deploy.md) para criar um
usuário de teste — a collection do Postman faz isso automaticamente):

```bash
API_URL=https://0wywnn2xkg.execute-api.us-east-2.amazonaws.com
CLIENT_ID=$(cd infra && terraform output -raw cognito_client_id)

ID_TOKEN=$(aws cognito-idp initiate-auth \
  --client-id "$CLIENT_ID" --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=estudante.teste,PASSWORD='SenhaForte#2026' \
  --query 'AuthenticationResult.IdToken' --output text)

# avaliação não crítica → 201, nivelUrgencia BAIXA, sem e-mail
curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ID_TOKEN" \
  -d '{"descricao": "Aula excelente, professor muito didático", "nota": 9}'

# avaliação crítica → 201, nivelUrgencia ALTA, dispara e-mail de alerta
curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ID_TOKEN" \
  -d '{"descricao": "Aula com problemas graves de áudio", "nota": 1}'

# sem token → 401 (comportamento esperado)
curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -d '{"descricao": "Sem autenticação", "nota": 5}'
```

## Deploy

`terraform apply` é **manual**, local, por quem tiver acesso à conta AWS do projeto — a conta usa
a "nova experiência AWS" (login social), cuja política de organização bloqueia a criação de
provedor OIDC, o que inviabiliza autenticação via OIDC para o GitHub Actions. O CI
(`.github/workflows/ci.yml`) roda testes e valida o Terraform a cada push, sem precisar de
credencial AWS nenhuma; `deploy.yml` builda e empacota os jars das 3 funções automaticamente.

Passo a passo completo (bootstrap do state remoto, primeiro apply, verificações de SES/SNS,
smoke test) em [`docs/deploy.md`](docs/deploy.md).

## Monitoramento

Cada função e o endpoint têm logs estruturados no CloudWatch (retenção de 30 dias), um dashboard
único consolidando invocações/erros/duração/DLQs/DynamoDB/API Gateway, e alarmes (erro, throttling,
duração perto do timeout, mensagens em DLQ, 5xx da API) publicando num tópico SNS com e-mail para o
administrador. Detalhes em [`docs/monitoramento.md`](docs/monitoramento.md).

## Decisões de arquitetura — por quê

- **3 funções com responsabilidade única** (acima do mínimo de 2 exigido): cada uma faz uma coisa
  só — receber, notificar ou relatar — testável e substituível isoladamente.
- **Classificação de urgência é regra de domínio**, calculada uma única vez em código
  (`modulo-dominio`); a infraestrutura (regra do EventBridge) só roteia com base no campo já
  calculado — nunca reimplementa a regra de negócio.
- **Clean Architecture em módulos Maven independentes**: domínio sem nenhuma dependência de
  framework/AWS, casos de uso dependendo só de portas (interfaces), adapters concretos isolados em
  `modulo-infraestrutura`.
- **E-mail do administrador nunca fica hardcoded** (SSM Parameter Store, lido a cada invocação) —
  o grupo precisou trocar esse e-mail uma vez durante o desenvolvimento, sem precisar de redeploy.
- **Resiliência de invocação assíncrona**: notificação de urgência e relatório semanal têm política
  de retry + DLQ dedicada — nenhuma falha é descartada silenciosamente.
- **IAM de privilégio mínimo**: cada função tem sua própria role, sem permissão além do
  estritamente necessário.
- **AWS Lambda SnapStart** nas 3 funções: cold start caiu de ~3.7-4s para ~1.6s (medido em
  produção). Exigiu um gancho CRaC para renovar as credenciais da execution role após a
  restauração do snapshot — sem isso, a função retomaria com credenciais potencialmente obsoletas.
  Detalhes em [`docs/arquitetura.md`](docs/arquitetura.md).

## Documentação completa

| Documento | Conteúdo |
|---|---|
| [`docs/arquitetura.md`](docs/arquitetura.md) | Modelo de cloud, componentes, decisões de design, segurança |
| [`docs/deploy.md`](docs/deploy.md) | Setup da conta AWS, bootstrap do Terraform, pipeline, smoke test |
| [`docs/monitoramento.md`](docs/monitoramento.md) | Logs, dashboard, alarmes |
| [`docs/funcoes.md`](docs/funcoes.md) | Gatilho, entrada/saída e IAM de cada função Lambda |
| [`docs/video.md`](docs/video.md) | Roteiro do vídeo de demonstração |
| [`postman/README.md`](postman/README.md) | Como usar a collection de testes |

## Integrantes do grupo

- Lucas Walim da Silva
- Pamela Mendes Ribeiro
- Rafael Oliveira Rodrigues Valle
- Rodrigo Eufrásio Daniel
- Rodrigo Cavalcante de Barros
