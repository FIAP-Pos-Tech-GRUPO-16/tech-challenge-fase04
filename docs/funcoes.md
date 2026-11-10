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
- `400 Bad Request` com `{ "mensagem": "..." }` quando a nota está fora de 0-10, a descrição está
  vazia (ou acima de 1000 caracteres), o corpo está ausente ou o JSON é inválido.
- `401 Unauthorized` quando o token está ausente ou inválido (resposta do API Gateway, a função
  nunca chega a ser invocada).

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
função lança exceção propositalmente — isso faz o EventBridge aplicar a política de retry e, se
persistir, mover o evento para a DLQ (`PublicacaoEventoFalhouException`/`EventoInvalidoException`
nunca são engolidas silenciosamente).

**Dados do e-mail de alerta** (conforme contrato do desafio): Descrição, Urgência, Data de envio.

**Efeitos colaterais**: lê o e-mail do administrador no SSM Parameter Store (a cada invocação, não
em cache — troca de destinatário tem efeito imediato); envia e-mail via SES.

**IAM (privilégio mínimo)**: `ses:SendEmail`/`ses:SendRawEmail` na identidade remetente;
`ssm:GetParameter` no parâmetro do e-mail do admin.

**Variáveis de ambiente**: `SES_REMETENTE`, `SSM_PARAMETRO_EMAIL_ADMIN`.

**Resiliência**: alvo assíncrono do EventBridge com retry (2 tentativas) + DLQ dedicada
(`dlq-notificar-urgencia`).

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
quantidade de avaliações por nível de urgência.

**Efeitos colaterais**: consulta o DynamoDB (via GSI `gsi_data_envio`, sem Scan completo); envia
e-mail via SES.

**IAM (privilégio mínimo)**: `dynamodb:Query` na tabela `avaliacoes` e no seu índice;
`ses:SendEmail`/`ses:SendRawEmail`; `ssm:GetParameter`.

**Variáveis de ambiente**: `TABELA_AVALIACOES`, `SES_REMETENTE`, `SSM_PARAMETRO_EMAIL_ADMIN`.

**Resiliência**: alvo assíncrono do EventBridge Scheduler com retry (2 tentativas) + DLQ dedicada
(`dlq-relatorio-semanal`).

**Teste manual sem esperar a semana**: `aws lambda invoke --function-name
tech-challenge-fase04-gerar-relatorio-semanal --payload '{}' saida.json` (ver
[`deploy.md`](deploy.md)).
