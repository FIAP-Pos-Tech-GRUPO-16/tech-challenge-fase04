# Collection Postman — Tech Challenge Fase 4

Testa os 3 componentes serverless do projeto contra o **deploy real na AWS** (não é preciso rodar
nada localmente para usar esta collection).

## Arquivos

- `tech-challenge-fase04.postman_collection.json` — a collection.
- `tech-challenge-fase04.postman_environment.json` — environment com as URLs/nomes já
  preenchidos para o deploy atual (conta AWS `827938108008`, região `us-east-2`).

## Como importar

1. Postman → **Import** → arraste os dois arquivos desta pasta.
2. No canto superior direito, selecione o environment **"Tech Challenge Fase 4 - AWS (deploy real)"**.

## Preencher as credenciais AWS (uma vez, no environment)

Necessário para as pastas **00** (criar usuário de teste) e **02/03** (invocar Lambda direto) —
todas usam AWS Signature v4 (suporte nativo do Postman, aba *Authorization*). Essa conta usa a
"nova experiência AWS" (login social) — as credenciais são temporárias. Com a AWS CLI já
configurada (ver `docs/deploy.md`), rode:

```
aws configure export-credentials --profile tech-challenge-fase04 --format env
```

Copie os três valores impressos (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
`AWS_SESSION_TOKEN`) para as variáveis de mesmo propósito no environment do Postman
(`aws_access_key_id`, `aws_secret_access_key`, `aws_session_token` — marcadas como *secret*, não
sincronizam com a nuvem do Postman). A sessão expira com o login da AWS CLI (até 12h) — repita o
comando acima quando expirar.

## Pasta 00 — Autenticação (Cognito)

`POST /avaliacao` exige um JWT do Cognito (JWT Authorizer no API Gateway). Rode as 3 requisições
desta pasta **em ordem, uma vez**, antes de usar a pasta 01:

1. **Criar usuário de teste** (AWS SigV4) — `AdminCreateUser`, idempotente (200 na primeira vez,
   400 `UsernameExistsException` depois, ambos aceitos pelo teste).
2. **Definir senha permanente** (AWS SigV4) — `AdminSetUserPassword` com `Permanent: true`, pula o
   desafio de troca de senha no primeiro login.
3. **Obter token** (`InitiateAuth`, chamada pública do Cognito — **não** usa AWS SigV4, é
   literalmente o que um app cliente faria no login) — o script de teste já captura o
   `IdToken` na variável de environment `id_token`, usada automaticamente por toda a pasta 01.

O token expira em 60 minutos — rode a requisição 3 de novo quando expirar (não precisa repetir 1
e 2).

## Pasta 01 — Receber Avaliação (API Gateway)

Testa o endpoint público `POST /avaliacao`. Exige o JWT da pasta 00 — a pasta inteira já está
configurada com `Authorization: Bearer {{id_token}}` (herdado por todas as requisições filhas).
Inclui casos de sucesso (um para cada nível de urgência), casos de erro de validação (nota fora do
intervalo, campo ausente, descrição vazia, descrição acima de 1000 caracteres, corpo vazio, JSON
malformado) e um caso específico **sem token**, confirmando que o endpoint realmente barra
requisições não autenticadas (`401`). Cada requisição já vem com scripts de teste (aba *Tests*)
validando status code e corpo da resposta.

Rodar a requisição **"Avaliação válida - urgência ALTA / crítica"** dispara de verdade a função
`notificar-urgencia` (via EventBridge) — um e-mail de alerta chega para o administrador em alguns
segundos.

## Pastas 02 e 03 — Notificar Urgência / Gerar Relatório Semanal (Lambda direto)

Essas duas funções **não são acionadas por HTTP público** (uma reage a evento do EventBridge, a
outra a um agendamento semanal) — por isso, para testar 100% do projeto a partir do Postman, essas
requisições chamam a **API de Invoke do Lambda** diretamente
(`POST https://lambda.{region}.amazonaws.com/2015-03-31/functions/{nome}:live/invocations` — o
sufixo `:live` é o alias que habilita o SnapStart), autenticada com AWS Signature v4.

### O que cada requisição valida

- **Notificar Urgência → evento de urgência ALTA**: envia o mesmo formato de evento que o
  EventBridge publica de verdade; espera `200` do Lambda e nenhum `X-Amz-Function-Error`.
- **Notificar Urgência → evento malformado**: confirma que a função rejeita (propositalmente,
  sem capturar a exceção) um evento sem o campo `detail` — é esse o comportamento que aciona o
  retry/DLQ em produção.
- **Gerar Relatório Semanal → invocação**: dispara a agregação e o envio do relatório por e-mail
  sem esperar a segunda-feira.

## Rodando tudo de uma vez

Collection Runner (Postman) → selecionar a collection + o environment → Run, na ordem das pastas
(00 → 01 → 02 → 03). Todas as requisições de erro das pastas 00/01 esperam falhar com `400`/`401`
(isso é o comportamento correto sendo validado, não um problema).
