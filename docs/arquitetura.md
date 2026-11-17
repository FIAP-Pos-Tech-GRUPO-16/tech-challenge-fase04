# Arquitetura da solução

## Visão geral

Plataforma serverless para recebimento de avaliações de aulas, notificação automática de
administradores em casos críticos e geração de relatório semanal consolidado — construída
inteiramente sobre AWS, usando apenas componentes serverless/gerenciados (sem servidores para
administrar).

```
                         ┌─────────────────────┐
  Estudante  ── POST ──▶ │   API Gateway (HTTP) │
                         └──────────┬───────────┘
                                    │ invoke
                                    ▼
                    ┌───────────────────────────────┐
                    │ Lambda: receber-avaliacao      │  (SRP: validar + persistir + publicar evento)
                    │ Spring Cloud Function           │
                    └───────┬───────────────┬────────┘
                            │ grava          │ publica evento AvaliacaoRecebida
                            ▼                ▼
                     ┌────────────┐   ┌──────────────────────────┐
                     │ DynamoDB    │   │ EventBridge (bus próprio) │
                     │ avaliacoes  │   └──────────┬────────────────┘
                     └─────┬──────┘               │ regra: nivelUrgencia = ALTA
                           │                        ▼
             consulta      │           ┌─────────────────────────────┐
             (GSI por data)│           │ Lambda: notificar-urgencia   │ (SRP: só notifica)
                           │           │ Spring Cloud Function         │
                           │           └──────────────┬───────────────┘
                           │                           ▼
                           │                      SES ──▶ e-mail do admin (SSM Parameter)
                           │
                    ┌──────▼─────────────────────────┐
                    │ EventBridge Scheduler (semanal) │
                    └──────────────┬──────────────────┘
                                   ▼
                    ┌───────────────────────────────────┐
                    │ Lambda: gerar-relatorio-semanal    │ (SRP: agrega + envia relatório)
                    │ Spring Cloud Function               │
                    └──────────────────┬──────────────────┘
                                       ▼
                                  SES ──▶ e-mail do admin (relatório)
```

## Modelo de cloud escolhido

**AWS, 100% serverless.** Nenhum componente exige provisionamento ou gestão de servidor.

- **Modelo de implantação:** nuvem pública (AWS), em uma única região (`us-east-2`, a região do
  projeto na nova experiência AWS), com um único ambiente.
- **Modelo de serviço:** FaaS (*Function as a Service*) para a lógica de negócio, com AWS Lambda,
  combinado com serviços gerenciados (*Backend as a Service*) para tudo o que não é código do
  grupo: banco (DynamoDB), mensageria (EventBridge), e-mail (SES), autenticação (Cognito),
  configuração (SSM) e observabilidade (CloudWatch). O grupo escreve e mantém só as funções e o
  Terraform; servidores, sistema operacional, escala e alta disponibilidade ficam com a AWS.
- **Infraestrutura como código:** todo recurso é declarado em Terraform e aplicado pelo pipeline.


| Camada | Serviço AWS | Por quê |
|---|---|---|
| Cômputo | Lambda | Paga por execução, escala automaticamente, atende à exigência obrigatória de serverless |
| Entrada HTTP | API Gateway (HTTP API) | Mais barato e simples que REST API; proxy direto para Lambda |
| Autenticação | Cognito + JWT Authorizer | Usuários gerenciados pela AWS; token validado no API Gateway antes de invocar a função |
| Banco de dados | DynamoDB | Serverless nativo, sem VPC (cold start menor), paga por uso |
| Mensageria/roteamento | EventBridge (bus + regra + Scheduler) | Desacopla ingestão de notificação; Scheduler cobre o agendamento semanal nativamente |
| E-mail | SES | Serviço de e-mail transacional nativo da AWS |
| Configuração | SSM Parameter Store | Guarda o e-mail do admin fora do código, mutável sem redeploy |
| Observabilidade | CloudWatch (Logs, Alarmes, Dashboard) + SNS | Nativo, sem agente adicional para instalar |
| Resiliência | SQS (DLQ) | Captura falhas de entrega e de execução das invocações assíncronas após as tentativas de retry |
| Deploy contínuo | CodePipeline V2 + CodeBuild + CodeConnections | Deploy a cada push sem credencial AWS no GitHub (OIDC bloqueado pela SCP da conta) |

## As 3 funções serverless (responsabilidade única)

O enunciado exige no mínimo 2; a solução tem 3, cada uma com uma única responsabilidade:

1. **`receber-avaliacao`** — endpoint HTTP (`POST /avaliacao`). Valida a entrada, persiste no
   DynamoDB e publica o evento `AvaliacaoRecebida` no EventBridge. Não decide quem reage ao
   evento — isso é responsabilidade da infraestrutura de roteamento (regra do EventBridge) e do
   próprio domínio (nível de urgência já vem calculado no evento).
2. **`notificar-urgencia`** — acionada pela regra do EventBridge quando `nivelUrgencia = ALTA`.
   Única responsabilidade: montar e enviar o e-mail de alerta via SES.
3. **`gerar-relatorio-semanal`** — acionada semanalmente pelo EventBridge Scheduler. Única
   responsabilidade: agregar as avaliações dos últimos 7 dias (média de notas, contagem por dia,
   contagem por urgência) e enviar o relatório via SES.

Ver [`funcoes.md`](funcoes.md) para o detalhamento de cada função (gatilho, entrada, saída, IAM).

## Decisões de design relevantes

- **Classificação de urgência é regra de domínio, não de infraestrutura.** O nível
  (ALTA/MÉDIA/BAIXA, a partir da nota 0-10) é calculado uma única vez, em código, dentro da
  entidade `Avaliacao` (`modulo-dominio`). A regra do EventBridge apenas roteia com base nesse
  campo já calculado — a infraestrutura nunca reimplementa a regra de negócio.
- **Clean Architecture em 3 módulos independentes**: `modulo-dominio` (zero dependência de
  framework/AWS), `modulo-aplicacao` (casos de uso + portas/interfaces), `modulo-infraestrutura`
  (adapters concretos para DynamoDB/SES/EventBridge/SSM via AWS SDK v2). Cada função Lambda é
  apenas uma composição fina (DI manual) ligando essas camadas a um handler HTTP/evento.
- **E-mail do admin nunca fica hardcoded.** Fica em um parâmetro SSM, lido a cada invocação —
  trocar o destinatário não exige novo deploy de código (o grupo já precisou trocar esse e-mail
  uma vez durante o desenvolvimento).
- **Resiliência de invocação assíncrona**: tanto a notificação de urgência quanto o relatório
  semanal são invocados de forma assíncrona (EventBridge e Scheduler), cada um com uma DLQ (SQS)
  dedicada que cobre os dois pontos de falha: **entrega** (o EventBridge não conseguiu invocar a
  Lambda: retry de 2 tentativas + `dead_letter_config` do alvo) e **execução** (a função lançou
  exceção: retry assíncrono de 2 tentativas da própria Lambda + destino *on-failure* no alias
  `live`). Nenhuma falha é descartada silenciosamente.
- **Endpoint público autenticado via Cognito, com throttling como camada complementar.** O
  enunciado não exige login para o estudante enviar feedback, mas a autenticação foi implementada
  mesmo assim (ver [seção dedicada mais abaixo](#autenticação-no-endpoint-público-cognito--jwt-authorizer)).
  Além do JWT Authorizer, o estágio do API Gateway tem um limite de taxa (throttling) como defesa
  adicional contra abuso, mesmo com autenticação já exigida.

## Segurança e governança de acesso

- Cada função Lambda tem sua própria IAM role com política de privilégio mínimo — nenhuma delas
  tem permissão além do estritamente necessário (ex.: `notificar-urgencia` só pode `ses:SendEmail`
  e `ssm:GetParameter`, nunca `dynamodb:*`).
- Deploy automatizado sem credencial AWS fora da conta: a "nova experiência AWS" nega
  `iam:CreateOpenIDConnectProvider` (confirmado em apply real), o que inviabiliza OIDC para o
  GitHub Actions. Em vez de guardar uma access key de longa duração no GitHub, o pipeline roda
  dentro da AWS (CodePipeline + CodeBuild) e lê o repositório via CodeConnections. A role de
  deploy só gerencia roles IAM com o prefixo da aplicação e não consegue alterar a si mesma
  (detalhes em [`deploy.md`](deploy.md#6-pipeline-de-deploy-contínuo-uma-única-vez)).
- Dados em trânsito: HTTPS nativo (API Gateway, SES). Dados em repouso: criptografia padrão do
  DynamoDB e SQS (SSE gerenciado pela AWS).
- Logs do CloudWatch com retenção limitada (30 dias) — evita acúmulo indefinido de dados.
- Nenhum segredo em código-fonte: o e-mail do administrador vem do SSM; credenciais AWS nunca
  aparecem no repositório.

## AWS Lambda SnapStart

Implementado nas 3 funções (`terraform apply` confirmado, `OptimizationStatus: On` nas três,
2026-11-10). Reduz o cold start de ~3.7-4s (init completo do Spring) para ~1.6s (restauração do
snapshot) — medido em produção.

- **Terraform**: cada função é publicada (`publish = true`) com `snap_start { apply_on =
  "PublishedVersions" }` — o SnapStart só vale para versões publicadas, nunca para `$LATEST`. Um
  alias `live` aponta sempre para a versão mais recente; API Gateway, EventBridge e o Scheduler
  invocam o alias, não a função base.
- **Credenciais pós-restauração**: o risco real do SnapStart é a execução retomar com as
  credenciais da execution role obtidas antes do snapshot. Resolvido com um gancho CRaC
  (`GanchoPosRestauracaoSnapStart`, em `modulo-infraestrutura`): todos os clientes AWS SDK de uma
  função compartilham a mesma instância de `AwsCredentialsProvider`, e o gancho força a renovação
  dessa instância em `afterRestore`, além de aquecer os clientes (`SdkWarmUp`) na inicialização.
  Exigiu subir o AWS SDK para Java de 2.46.7 para **2.57.12** (a versão real mais recente — o
  buscador do Maven Central estava reportando uma versão desatualizada) para ter acesso ao
  `SdkWarmUp`.

## Fora do escopo do MVP (evolução futura)

- **Múltiplos ambientes** (dev/stage/prod) — um único ambiente é suficiente para o escopo
  acadêmico.
- **Auto-cadastro de estudantes com verificação de e-mail**: o User Pool do Cognito não pede
  e-mail verificado nem tem fluxo de sign-up público — usuários de teste são criados via admin
  (`AdminCreateUser`). Suficiente para este desafio; um produto real precisaria de sign-up
  self-service.

## Autenticação no endpoint público (Cognito + JWT Authorizer)

O enunciado não exige autenticação em `POST /avaliacao` — foi implementada mesmo assim, por
decisão do grupo. Avaliação de alternativas antes de implementar (API Gateway HTTP API v2 não
suporta API Keys/usage plans nativos — exclusivo de REST API v1):

| Opção | O que muda | Trade-off |
|---|---|---|
| Lambda Authorizer com chave compartilhada | Pouco esforço | Não é autenticação de verdade — qualquer um com a chave acessa |
| **Amazon Cognito + JWT Authorizer (escolhida)** | Estudantes passam a ter conta/login real | Muda o contrato do produto; mais complexidade operacional |
| IAM/SigV4 | Reusa infra AWS existente | Só serve para chamador com credenciais AWS, não para estudante anônimo |

**Implementação** (`infra/modules/cognito/`):
- `aws_cognito_user_pool` + `aws_cognito_user_pool_client` (cliente público, sem secret, fluxo
  `USER_PASSWORD_AUTH` habilitado — permite obter token via CLI/Postman sem SECRET_HASH).
- `aws_apigatewayv2_authorizer` do tipo `JWT` no API Gateway, `audience` = client ID, `issuer` =
  URL do User Pool — anexado à rota `POST /avaliacao` (`authorization_type = "JWT"`).
- A função `receber-avaliacao` **não tem nenhum código de autenticação** — o API Gateway rejeita
  requisições sem um JWT válido antes mesmo de invocar a Lambda. Confirmado em produção: sem
  token → `401`; com token de um usuário autenticado → `201`.
- Usuários de teste são criados via `AdminCreateUser` + `AdminSetUserPassword` (admin, bypassa
  verificação de e-mail) — ver `docs/deploy.md` e `postman/README.md`.
