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

## Pasta 1 — Receber Avaliação (API Gateway)

Testa o endpoint público `POST /avaliacao` diretamente — **não precisa de nenhuma credencial**,
o endpoint não exige autenticação (por desenho do projeto). Inclui casos de sucesso (um para cada
nível de urgência) e casos de erro (nota fora do intervalo, campo ausente, descrição vazia,
descrição acima de 1000 caracteres, corpo vazio, JSON malformado). Cada requisição já vem com
scripts de teste (aba *Tests*) validando status code e corpo da resposta.

Rodar a requisição **"Avaliação válida - urgência ALTA / crítica"** dispara de verdade a função
`notificar-urgencia` (via EventBridge) — um e-mail de alerta chega para o administrador em alguns
segundos.

## Pastas 2 e 3 — Notificar Urgência / Gerar Relatório Semanal (Lambda direto)

Essas duas funções **não são acionadas por HTTP público** (uma reage a evento do EventBridge, a
outra a um agendamento semanal) — por isso, para testar 100% do projeto a partir do Postman, essas
requisições chamam a **API de Invoke do Lambda** diretamente
(`POST https://lambda.{region}.amazonaws.com/2015-03-31/functions/{nome}/invocations`), autenticada
com **AWS Signature v4** (suporte nativo do Postman, aba *Authorization* de cada pasta).

### Preencher as credenciais (uma vez, no environment)

Essa conta usa a "nova experiência AWS" (login social) — as credenciais são temporárias.
Com a AWS CLI já configurada (ver `docs/deploy.md`), rode:

```
aws configure export-credentials --profile tech-challenge-fase04 --format env
```

Copie os três valores impressos (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
`AWS_SESSION_TOKEN`) para as variáveis de mesmo propósito no environment do Postman
(`aws_access_key_id`, `aws_secret_access_key`, `aws_session_token` — marcadas como *secret*, não
sincronizam com a nuvem do Postman). A sessão expira com o login da AWS CLI (até 12h) — repita o
comando acima quando expirar.

### O que cada requisição valida

- **Notificar Urgência → evento de urgência ALTA**: envia o mesmo formato de evento que o
  EventBridge publica de verdade; espera `200` do Lambda e nenhum `X-Amz-Function-Error`.
- **Notificar Urgência → evento malformado**: confirma que a função rejeita (propositalmente,
  sem capturar a exceção) um evento sem o campo `detail` — é esse o comportamento que aciona o
  retry/DLQ em produção.
- **Gerar Relatório Semanal → invocação**: dispara a agregação e o envio do relatório por e-mail
  sem esperar a segunda-feira.

## Rodando tudo de uma vez

Collection Runner (Postman) → selecionar a collection + o environment → Run. Todas as requisições
de erro esperam falhar com `400` (isso é o comportamento correto sendo validado, não um problema).
