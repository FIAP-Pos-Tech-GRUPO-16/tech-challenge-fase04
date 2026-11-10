# Configuração do monitoramento

Toda a observabilidade é provisionada via Terraform (`infra/modules/cloudwatch`), junto com o
resto da infraestrutura — não é um passo manual separado.

## Logs

Cada função Lambda e o estágio do API Gateway têm seu próprio CloudWatch Log Group, criado
explicitamente pelo Terraform (não o grupo automático e sem retenção definida que a AWS cria por
padrão), com **retenção de 30 dias**:

- `/aws/lambda/tech-challenge-fase04-receber-avaliacao`
- `/aws/lambda/tech-challenge-fase04-notificar-urgencia`
- `/aws/lambda/tech-challenge-fase04-gerar-relatorio-semanal`
- `/aws/apigateway/tech-challenge-fase04-api` (log de acesso: IP, método, rota, status, erro de
  integração por requisição)

## Dashboard

Um dashboard único (`tech-challenge-fase04-visao-geral`) consolida:

- Invocações por função (volume de uso)
- Erros por função
- Duração por função (p99)
- Mensagens visíveis nas DLQs (falhas que sobreviveram ao retry)
- Capacidade consumida no DynamoDB (leitura/escrita)
- Requisições e erros 4xx/5xx no API Gateway

URL exposta no output `url_dashboard` do Terraform (`terraform output url_dashboard`).

## Alarmes

Todos publicam no tópico SNS `tech-challenge-fase04-alertas-operacionais`, com uma inscrição por
e-mail para o administrador (`var.email_admin_inicial`):

| Alarme | Condição |
|---|---|
| `<funcao>-erros` | Qualquer erro em uma função nos últimos 5 minutos |
| `<funcao>-throttles` | Qualquer throttling em uma função nos últimos 5 minutos |
| `<funcao>-duracao-proxima-do-timeout` | Duração máxima > 80% do timeout configurado |
| `dlq-<alvo>-com-mensagens` | Qualquer mensagem visível em uma DLQ — indica falha que sobreviveu ao retry |

> A inscrição por e-mail do SNS exige confirmação manual (a AWS envia um link de confirmação para
> a caixa de entrada do administrador) — igual à verificação de identidade do SES. Sem confirmar,
> os alarmes disparam mas o e-mail não chega.

## Rastreamento (tracing)

Não habilitado no MVP (X-Ray). Dado o volume baixo esperado e a ausência de múltiplos serviços
encadeados síncronos (o fluxo é majoritariamente assíncrono via EventBridge), os logs estruturados
e os alarmes de erro/DLQ já dão visibilidade suficiente sobre falhas. Fica documentado como
evolução natural se o sistema crescer.

## Como verificar que o monitoramento está funcionando

1. `terraform output url_dashboard` → abrir o link e confirmar que os 6 widgets carregam dados
   (mesmo que zerados, antes de qualquer uso real).
2. Forçar um erro proposital (ex.: invocar `notificar-urgencia` com um evento malformado via
   console AWS) e confirmar que o alarme `notificar-urgencia-erros` muda para `ALARM` e o e-mail
   chega.
3. Conferir os logs de uma invocação real em
   `/aws/lambda/tech-challenge-fase04-receber-avaliacao` no CloudWatch Logs Insights.
