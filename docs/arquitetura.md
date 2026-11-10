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

**AWS, 100% serverless.** Nenhum componente exige provisionamento ou gestão de servidor:

| Camada | Serviço AWS | Por quê |
|---|---|---|
| Cômputo | Lambda | Paga por execução, escala automaticamente, atende à exigência obrigatória de serverless |
| Entrada HTTP | API Gateway (HTTP API) | Mais barato e simples que REST API; proxy direto para Lambda |
| Banco de dados | DynamoDB | Serverless nativo, sem VPC (cold start menor), paga por uso |
| Mensageria/roteamento | EventBridge (bus + regra + Scheduler) | Desacopla ingestão de notificação; Scheduler cobre o agendamento semanal nativamente |
| E-mail | SES | Serviço de e-mail transacional nativo da AWS |
| Configuração | SSM Parameter Store | Guarda o e-mail do admin fora do código, mutável sem redeploy |
| Observabilidade | CloudWatch (Logs, Alarmes, Dashboard) + SNS | Nativo, sem agente adicional para instalar |
| Resiliência | SQS (DLQ) | Captura falhas de processamento assíncrono após as tentativas de retry |

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
  semanal são alvos assíncronos do EventBridge, cada um com política de retry (2 tentativas) e
  uma DLQ (SQS) dedicada — nenhuma falha é descartada silenciosamente.
- **Sem autenticação no endpoint público.** O enunciado não exige login para o estudante enviar
  feedback; a defesa contra abuso é um limite de taxa (throttling) no estágio do API Gateway.
  Documentado como possível evolução futura.

## Segurança e governança de acesso

- Cada função Lambda tem sua própria IAM role com política de privilégio mínimo — nenhuma delas
  tem permissão além do estritamente necessário (ex.: `notificar-urgencia` só pode `ses:SendEmail`
  e `ssm:GetParameter`, nunca `dynamodb:*`).
- Deploy (`terraform apply`) é feito localmente por quem tiver `aws login` na conta — a conta usa
  a "nova experiência AWS", cuja Service Control Policy nega `iam:CreateOpenIDConnectProvider`,
  o que inviabiliza autenticação via OIDC para o GitHub Actions (avaliado e descartado após
  confirmar o bloqueio em apply real). O GitHub Actions cuida de testes e build/empacotamento dos
  jars automaticamente a cada push — nenhuma credencial AWS fica no CI.
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

- **Autenticação no endpoint público** — ver discussão de alternativas e trade-offs abaixo.
- **Múltiplos ambientes** (dev/stage/prod) — um único ambiente é suficiente para o escopo
  acadêmico.

## Autenticação no endpoint público — avaliado, não implementado

O enunciado não exige autenticação em `POST /avaliacao`, e a defesa atual contra abuso é o
throttling do API Gateway (10 burst / 5 req/s). Avaliar autenticação de verdade aqui esbarra numa
limitação técnica real: **API Gateway HTTP API (v2) não suporta API Keys/usage plans nativos**
(isso é exclusivo de REST API v1) — para proteger o endpoint seria preciso um destes caminhos,
cada um com um trade-off bem diferente:

| Opção | O que muda | Trade-off |
|---|---|---|
| **Lambda Authorizer com chave compartilhada** (header `x-api-key` validado por uma função extra) | Pouco esforço, sem conceito de usuário | É só "um segredo a mais", não autenticação de verdade — qualquer um com a chave acessa |
| **Amazon Cognito + JWT Authorizer** | Estudantes passam a ter conta/login de verdade | Muda o contrato do produto (o enunciado não pressupõe login); custo e complexidade operacional bem maiores |
| **IAM/SigV4** (como as invocações diretas de Lambda na collection Postman) | Reusa infraestrutura AWS existente | Só funciona para chamadores com credenciais AWS — não serve para um estudante anônimo |

Nenhuma dessas opções é um "liga e pronto": a primeira não é proteção real, a segunda muda a
experiência do produto, a terceira não serve para o público-alvo do endpoint. Por isso ficou
documentada como evolução futura em vez de implementada "para ter alguma coisa" — a decisão de
qual caminho seguir depende de um requisito de produto que o enunciado não define.
