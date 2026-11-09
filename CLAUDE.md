# CLAUDE.md

Contexto e regras deste projeto para qualquer sessão futura do Claude Code.

## O que é este projeto

Tech Challenge da **Fase 4** da Pós-Graduação FIAP em **Arquitetura e Desenvolvimento Java** (grupo 16).
Enunciado completo em `docs/ADJT-Fase4-Tech-Challenge.pdf`.

**Problema:** plataforma de feedback de aulas. Estudantes enviam avaliações (nota 0-10 + descrição).
Administradores precisam ser notificados automaticamente quando um feedback é crítico, e recebem um
relatório semanal consolidado.

**Exigências obrigatórias do enunciado** (não negociáveis):
- Aplicação **serverless**, rodando em **ambiente cloud**.
- **Mínimo de 2 funções serverless**, cada uma com **responsabilidade única** — a separação correta
  de serviços é critério de avaliação.
- Ambiente cloud com segurança de dados e governança de acesso.
- Banco de dados / componentes de suporte configurados.
- Deploy automatizado dos componentes atualizáveis.
- Aplicação monitorada.
- Notificação automática para administradores em itens críticos.
- Relatório semanal dos feedbacks com média de avaliações.
- Entrega: repositório com código-fonte + vídeo demonstrando a aplicação, as funções serverless
  ativas e as configurações do projeto.

Contrato de entrada dado no enunciado:
```
POST /avaliacao
{ "descricao": string, "nota": int (0 a 10) }
```

## Decisões de arquitetura (já fechadas — não reabrir sem motivo forte)

| Decisão | Escolha |
|---|---|
| Cloud | AWS |
| IaC / deploy automatizado | Terraform |
| Runtime das funções | Spring Cloud Function sobre AWS Lambda, Java 21 |
| Banco de dados | DynamoDB |
| Testes locais | LocalStack + Testcontainers (não gastar créditos de cloud em dev) |
| Classificação de urgência | nota 0-3 = ALTA · 4-6 = MÉDIA · 7-10 = BAIXA (regra de domínio, não de infra) |
| E-mail do admin (SES) | Parametrizado via SSM Parameter Store — **nunca hardcoded**, porque vai trocar |
| Roteamento evento → notificação | EventBridge (bus próprio), regra filtra pelo campo `nivelUrgencia` já calculado no domínio |
| Resiliência de invocação assíncrona | DLQ (SQS) em cada alvo assíncrono do EventBridge |

**3 funções serverless** (acima do mínimo exigido):
1. `receber-avaliacao` — valida entrada, persiste no DynamoDB, publica evento `AvaliacaoRecebida`.
2. `notificar-urgencia` — reage a eventos com `nivelUrgencia = ALTA`, envia e-mail via SES.
3. `gerar-relatorio-semanal` — disparada por agendamento (EventBridge Scheduler), agrega dados e
   envia relatório via SES.

Fora do escopo do MVP (documentar como evolução futura, não implementar sem pedido explícito):
AWS Lambda SnapStart, autenticação no endpoint público, múltiplos ambientes (dev/stage/prod).

O plano de execução completo e detalhado por fases está em
`/Users/rodrigo/.claude/plans/partitioned-scribbling-neumann.md` (fora do repositório — se este
arquivo não existir mais, a estrutura de módulos abaixo e o histórico de commits são a fonte da
verdade).

## Estrutura do repositório

```
tech-challenge-fase04/
├── pom.xml                        (parent Maven)
├── modulo-dominio/                (entidades + regras puras — zero dependência de framework/AWS)
├── modulo-aplicacao/              (casos de uso + portas/interfaces)
├── modulo-infraestrutura/         (adapters: DynamoDB, SES, EventBridge via AWS SDK v2)
├── funcao-receber-avaliacao/      (handler Spring Cloud Function + composição)
├── funcao-notificar-urgencia/
├── funcao-gerar-relatorio-semanal/
├── infra/                         (Terraform: dynamodb, lambda, api-gateway, eventbridge, ses,
│                                    iam, ssm, cloudwatch, sqs)
├── .github/workflows/             (ci.yml, deploy.yml)
├── docs/                          (arquitetura, deploy, monitoramento, roteiro do vídeo)
└── CLAUDE.md
```

Dependências entre camadas (Clean Architecture, regra de dependência de dentro para fora):
`modulo-dominio` ← `modulo-aplicacao` ← `modulo-infraestrutura` / `funcao-*`. O domínio nunca
depende de Spring, AWS SDK ou qualquer framework. Os casos de uso dependem apenas de portas
(interfaces) definidas no próprio `modulo-aplicacao`; as implementações concretas ficam em
`modulo-infraestrutura`.

## Convenções de código

- Identificadores (classes, métodos, variáveis, pacotes de domínio) em **Português do Brasil**,
  claros e intuitivos. Ex.: `Avaliacao`, `NivelUrgencia`, `ClassificadorUrgencia`,
  `RepositorioAvaliacao`, `ReceberAvaliacaoCasoDeUso`.
- Clean Code, Clean Architecture e SOLID em todo o código. Sem abstrações especulativas, sem
  `GenericRepository`/`BaseController`, sem overengineering.
- Clientes AWS SDK (DynamoDB/SES/EventBridge) instanciados uma única vez fora do handler
  (reaproveitados entre invocações quentes do Lambda) — cold start e custo importam aqui.
- Testes: unitário puro para domínio/aplicação (JUnit5 + AssertJ + Mockito); integração com
  LocalStack via Testcontainers para os adapters de infraestrutura.

## Convenções de commit (regra rígida, não pular)

- **Um commit = uma responsabilidade.** Não misturar mudanças não relacionadas.
- Mensagem **curta e direta em PT-BR**, sem corpo longo, sem listas, sem bullet points.
  Ex.: `adiciona entidade Avaliacao e regra de classificação de urgência`.
- **Nunca incluir trailer de co-autoria/atribuição do Claude** (sem `Co-Authored-By`, sem link de
  sessão) — instrução explícita do usuário para este projeto, vale para todos os commits e PRs.
- Usar somente o usuário `digonexs` (já autenticado via `gh`/git nesta máquina) — não adicionar
  outros autores/co-autores.
- Preferir `git add <arquivos específicos>` a `git add -A`/`git add .`.

## Ambiente local

- Java 21 (toolchain local é JDK 26, compilar com `--release 21` para compatibilidade com o
  runtime Lambda `java21`).
- Maven 3.9+, Docker (para LocalStack/Testcontainers), `gh` autenticado como `digonexs`.
- AWS CLI/Terraform/SAM **ainda não instalados** nesta máquina — instalar quando a Fase 4/6 do
  plano chegar lá.
- Conta AWS: usuário ainda não tem credenciais configuradas — etapa guiada antes de qualquer
  `terraform apply` real.
