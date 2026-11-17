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
- `/aws/codebuild/tech-challenge-fase04-deploy` (saída de cada execução do pipeline de deploy)

### Logs estruturados das funções

As funções escrevem logs de aplicação em **JSON, uma linha por evento**, usando o logging
estruturado nativo do Spring Boot (`logging.structured.format.console=logstash` no
`application.properties` de cada função). Os campos de negócio entram como chaves próprias (API
fluente do SLF4J, `addKeyValue`), então o CloudWatch Logs Insights descobre os campos sozinho:

| Função | Evento registrado | Campos |
|---|---|---|
| `receber-avaliacao` | Avaliação recebida, persistida e evento publicado (`INFO`) | `avaliacaoId`, `nota`, `nivelUrgencia` |
| `receber-avaliacao` | Avaliação rejeitada por entrada inválida (`WARN`) | `motivo` |
| `notificar-urgencia` | Evento `AvaliacaoRecebida` processado (`INFO`) | `avaliacaoId`, `nivelUrgencia` |
| `notificar-urgencia` | Evento malformado, invocação vai falhar (`ERROR`) | `causa` |
| `notificar-urgencia` | E-mail de alerta enviado (`INFO`) | `tipoEmail`, `avaliacaoId`, `sesMessageId` |
| `gerar-relatorio-semanal` | Início e fim da geração (`INFO`) | — |
| `gerar-relatorio-semanal` | E-mail do relatório enviado (`INFO`) | `tipoEmail`, `totalAvaliacoes`, `mediaNotas`, `sesMessageId` |

A descrição da avaliação (texto livre do estudante) não vai para o log, só para o e-mail do
administrador. Exemplo de consulta no Logs Insights:

```
fields @timestamp, level, message, avaliacaoId, nivelUrgencia
| filter ispresent(avaliacaoId)
| sort @timestamp desc
```

## Dashboard

Um dashboard único (`tech-challenge-fase04-visao-geral`) consolida:

- Invocações por função (volume de uso)
- Erros por função
- Duração por função (p99)
- Mensagens visíveis nas DLQs (falhas de entrega ou de execução que sobreviveram ao retry)
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
| `api-gateway-5xx` | Qualquer resposta 5xx no API Gateway nos últimos 5 minutos — cobre falhas na camada de integração/API que não aparecem como `Errors` do Lambda |

> A inscrição por e-mail do SNS exige confirmação manual (a AWS envia um link de confirmação para
> a caixa de entrada do administrador) — igual à verificação de identidade do SES. Sem confirmar,
> os alarmes disparam mas o e-mail não chega.

## Rastreamento (tracing)

Não habilitado no MVP (X-Ray). Dado o volume baixo esperado e a ausência de múltiplos serviços
encadeados síncronos (o fluxo é majoritariamente assíncrono via EventBridge), os logs estruturados
(com `avaliacaoId` em todas as etapas, o que já permite seguir uma avaliação de ponta a ponta) e os
alarmes de erro/DLQ já dão visibilidade suficiente sobre falhas. Fica documentado como
evolução natural se o sistema crescer.

## Como verificar que o monitoramento está funcionando

1. `terraform output url_dashboard` → abrir o link e confirmar que os 6 widgets carregam dados
   (mesmo que zerados, antes de qualquer uso real).
2. Forçar um erro proposital publicando um evento malformado **no barramento** (caminho
   assíncrono real), por exemplo:
   ```
   aws events put-events --profile tech-challenge-fase04 --entries '[{"EventBusName":"tech-challenge-fase04-feedback","Source":"teste.manual","DetailType":"AvaliacaoRecebida","Detail":"{\"nivelUrgencia\":\"ALTA\"}"}]'
   ```
   Confirmar que o alarme `notificar-urgencia-erros` muda para `ALARM` e o e-mail chega e, alguns
   minutos depois (após as 2 novas tentativas), que a mensagem aparece na DLQ e dispara o alarme
   `dlq-notificar-urgencia-com-mensagens`.
3. Conferir os logs de uma invocação real em
   `/aws/lambda/tech-challenge-fase04-receber-avaliacao` no CloudWatch Logs Insights.
