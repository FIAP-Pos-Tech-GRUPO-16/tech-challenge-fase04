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

## Fora do escopo do MVP (evolução futura)

- **AWS Lambda SnapStart**: reduziria o cold start do Spring, mas exige tratamento explícito de
  credenciais/estado pós-restore (risco não justificado para este desafio).
- **Autenticação no endpoint público**.
- **Múltiplos ambientes** (dev/stage/prod) — um único ambiente é suficiente para o escopo
  acadêmico.
