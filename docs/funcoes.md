# Documentação das funções

Todas as 3 funções são Lambdas Java 21, implementadas com Spring Cloud Function
(`org.springframework.cloud.function.adapter.aws.FunctionInvoker` como handler), empacotadas como
jar sombreado (maven-shade-plugin) e com a classe `@SpringBootApplication` de cada uma apontada
pela variável de ambiente `MAIN_CLASS` (configurada no Terraform).

Código-fonte: `funcao-receber-avaliacao/`, `funcao-notificar-urgencia/`,
`funcao-gerar-relatorio-semanal/`.

---

## `receber-avaliacao`

| | |
|---|---|
| Responsabilidade única | Validar, persistir e publicar o evento de uma avaliação recebida |
| Gatilho | API Gateway (HTTP API) — `POST /avaliacao`, protegido por JWT Authorizer (Cognito) |
| Classe principal | `br.com.fiap.techchallenge.feedback.funcao.receberavaliacao.AplicacaoReceberAvaliacao` |
| Handler (classe) | `HandlerReceberAvaliacao implements Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>` |

**Autenticação**: exige header `Authorization: Bearer <JWT do Cognito>` — validado inteiramente
pelo API Gateway antes de invocar a função; o handler/caso de uso não tem nenhum código de
autenticação. Sem token válido, a requisição nem chega na Lambda (`401`). Ver
[`arquitetura.md`](arquitetura.md) e `postman/README.md` (pasta 00) para como obter um token de
teste.

**Entrada** (corpo da requisição HTTP, conforme contrato do desafio):
```json
{ "descricao": "string", "nota": 0 }
```

**Saída**:
- `201 Created` com `{ "id": "uuid", "nivelUrgencia": "ALTA|MEDIA|BAIXA" }` quando válida.
- `400 Bad Request` com `{ "mensagem": "..." }` quando a nota está fora de 0-10, o campo `nota`
  está ausente, a descrição está vazia (ou acima de 1000 caracteres), o corpo está ausente, o JSON
  é inválido ou traz um campo não reconhecido.
- `401 Unauthorized` quando o token está ausente ou inválido (resposta do API Gateway, a função
  nunca chega a ser invocada).
- `500 Internal Server Error` (gerado pelo API Gateway) se a gravação no DynamoDB ou a publicação no
  EventBridge falhar: a função não engole o erro, a invocação falha e o alarme
  `receber-avaliacao-erros` dispara.

**Efeitos colaterais**: grava um item na tabela DynamoDB `avaliacoes`; publica o evento
`AvaliacaoRecebida` (detail-type) no barramento EventBridge do projeto, já com o campo
`nivelUrgencia` calculado.

**IAM (privilégio mínimo)**: `dynamodb:PutItem` na tabela `avaliacoes`; `events:PutEvents` no
barramento do projeto.

**Variáveis de ambiente**: `TABELA_AVALIACOES`, `EVENTBRIDGE_BARRAMENTO`.

---

## `notificar-urgencia`

| | |
|---|---|
| Responsabilidade única | Enviar alerta por e-mail quando uma avaliação é crítica |
| Gatilho | Regra do EventBridge: `detail-type = AvaliacaoRecebida` **e** `detail.nivelUrgencia = ALTA` |
| Classe principal | `br.com.fiap.techchallenge.feedback.funcao.notificarurgencia.AplicacaoNotificarUrgencia` |
| Handler (classe) | `HandlerNotificarUrgencia implements Function<String, String>` |

**Entrada**: o envelope padrão do EventBridge (string JSON), do qual só o campo `detail` é lido:
```json
{ "detail": { "id": "...", "descricao": "...", "nota": 2, "nivelUrgencia": "ALTA", "dataEnvio": "..." } }
```

**Saída**: string de confirmação (`"Avaliação processada: <id>"`). Se o evento vier malformado, a
função lança `EventoInvalidoException` propositalmente (nunca engole o erro). Como o EventBridge
invoca a função de forma **assíncrona**, quem trata essa falha é a própria Lambda: faz mais 2
tentativas e, se a função continuar falhando, o destino *on-failure* do alias `live` grava o evento
original + o erro na DLQ `dlq-notificar-urgencia`.

**Dados do e-mail de alerta** (conforme contrato do desafio): Descrição, Urgência, Data de envio.

**Efeitos colaterais**: lê o e-mail do administrador no SSM Parameter Store (a cada invocação, não
em cache — troca de destinatário tem efeito imediato); envia e-mail via SES.

**IAM (privilégio mínimo)**: `ses:SendEmail`/`ses:SendRawEmail` na identidade remetente;
`ssm:GetParameter` no parâmetro do e-mail do admin; `sqs:SendMessage` na própria DLQ (destino de
falha).

**Variáveis de ambiente**: `SES_REMETENTE`, `SSM_PARAMETRO_EMAIL_ADMIN`.

**Resiliência**: a DLQ dedicada (`dlq-notificar-urgencia`) recebe os dois tipos de falha:
- **Falha de entrega** (o EventBridge não conseguiu invocar a Lambda): retry do alvo (2 tentativas)
  + `dead_letter_config` da regra.
- **Falha de execução** (a função lançou exceção): retry assíncrono da Lambda (2 tentativas) +
  destino *on-failure* (`aws_lambda_function_event_invoke_config`).

---

## `gerar-relatorio-semanal`

| | |
|---|---|
| Responsabilidade única | Agregar as avaliações da semana e enviar o relatório por e-mail |
| Gatilho | EventBridge Scheduler — toda segunda-feira, 08:00 BRT (`cron(0 11 ? * MON *)` UTC) |
| Classe principal | `br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal.AplicacaoGerarRelatorioSemanal` |
| Handler (classe) | `HandlerGerarRelatorioSemanal implements Function<Map<String,Object>, String>` |

**Entrada**: payload do agendamento (irrelevante — o período do relatório é calculado a partir do
relógio do sistema, últimos 7 dias).

**Saída**: string de confirmação (`"Relatório semanal gerado e enviado"`).

**Dados do relatório** (conforme contrato do desafio): lista das avaliações do período (cada uma
com descrição, urgência, data de envio), média das notas, quantidade de avaliações por dia e
quantidade de avaliações por nível de urgência. As contagens diárias usam o fuso
`America/Sao_Paulo`, para que avaliações próximas à meia-noite sejam atribuídas ao dia do calendário
brasileiro. A janela dos últimos 7 dias continua baseada em instantes de tempo.

**Efeitos colaterais**: consulta o DynamoDB (via GSI `gsi_data_envio`, sem Scan completo); envia
e-mail via SES.

**IAM (privilégio mínimo)**: `dynamodb:Query` na tabela `avaliacoes` e no seu índice;
`ses:SendEmail`/`ses:SendRawEmail`; `ssm:GetParameter`; `sqs:SendMessage` na própria DLQ.

**Variáveis de ambiente**: `TABELA_AVALIACOES`, `SES_REMETENTE`, `SSM_PARAMETRO_EMAIL_ADMIN`.

**Resiliência**: mesma estratégia da `notificar-urgencia`, com a DLQ `dlq-relatorio-semanal`:
falha de entrega pelo retry + `dead_letter_config` do Scheduler; falha de execução pelo retry
assíncrono da Lambda + destino *on-failure*.

**Teste manual sem esperar a semana** (ver [`deploy.md`](deploy.md)):
```
aws lambda invoke --profile tech-challenge-fase04 \
  --function-name tech-challenge-fase04-gerar-relatorio-semanal:live \
  --cli-binary-format raw-in-base64-out --payload '{}' saida.json
```
