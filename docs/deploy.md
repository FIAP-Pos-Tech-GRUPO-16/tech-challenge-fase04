# Instruções de deploy

O `terraform apply` é **manual**, feito localmente por quem tiver acesso à conta AWS do projeto.
A conta usa a "nova experiência AWS" (login social, estrutura de "projeto"), cuja Service Control
Policy nega `iam:CreateOpenIDConnectProvider` — isso inviabiliza autenticação via OIDC para o
GitHub Actions (testado e confirmado em apply real), então não existe deploy 100% automatizado a
partir do CI para este tipo de conta. O GitHub Actions cuida do que não depende de credencial AWS:
testes (unitários + integração) a cada push/PR e empacotamento dos jars das 3 funções.

## 0. Pré-requisitos

- Conta AWS criada via **nova experiência AWS** (login social, estrutura de "projeto" — camada
  gratuita/free tier é suficiente para este desafio). Essa conta trava **todos** os recursos
  regionais numa única região (aqui, `us-east-2`) — já refletido em todo o Terraform/CI.
- `aws` CLI v2, `terraform` (>= 1.9), `uv` e Java 21 instalados localmente (ver
  [AWS Agent Toolkit](https://github.com/aws/agent-toolkit-for-aws) para o setup guiado).
- Build das 3 funções feito (`mvn -DskipTests package` na raiz) — o Terraform referencia os jars
  em `funcao-*/target/*.jar`.
- Essa conta usa `AccountFullAccessRole` (equivalente a admin) assumida via login — não existe
  "usuário IAM com access key" para criar manualmente como num setup clássico de AWS.

## 1. Autenticar localmente

```
aws login --region us-east-2 --profile tech-challenge-fase04
```

Abre o navegador para autenticação (login social). A sessão fica válida por 12h e pode ser
renovada por até 90 dias sem reabrir o navegador (rodar `aws login` de novo quando expirar).

**O Terraform não entende o tipo de credencial do `aws login` diretamente** (`aws configure list`
mostra `access_key`/`secret_key` do tipo `login`, que o SDK usado pelo Terraform não reconhece).
A correção é um profile adicional com `credential_process`, que delega a resolução de volta para
o `aws` CLI — configure uma vez em `~/.aws/config`:

```ini
[profile tech-challenge-fase04-terraform]
region = us-east-2
credential_process = aws configure export-credentials --profile tech-challenge-fase04 --format process
```

Use **esse** profile (com `-terraform` no nome) em todo comando Terraform daqui pra frente:
```
export AWS_PROFILE=tech-challenge-fase04-terraform
```

Confirme o acesso (com qualquer um dos dois profiles):
```
aws sts get-caller-identity --profile tech-challenge-fase04
```

## 2. Bootstrap do state remoto (uma única vez)

```
cd infra/bootstrap
terraform init
terraform apply
```

Cria o bucket S3 (`tech-challenge-fase04-terraform-state-<account-id>` — nomes de bucket são
globais entre todas as contas AWS, por isso o ID da conta entra no nome) e a tabela DynamoDB
(`tech-challenge-fase04-terraform-lock`) usados pelo backend remoto do módulo principal. Conferir
o nome real do bucket no output `bucket_state` e atualizar `infra/backend.hcl` se divergir.

## 3. Primeiro apply do módulo principal

```
cd infra
mvn -DskipTests package -f ..   # garante que os 3 jars existem antes do apply
terraform init -backend-config=backend.hcl
terraform plan \
  -var="email_admin_inicial=<email-do-admin>" \
  -var="email_remetente=<email-remetente-verificavel>" \
  -out=tfplan
terraform apply tfplan
```

Se `email_admin_inicial` e `email_remetente` forem o mesmo e-mail (comum para este desafio), o
Terraform cria só uma identidade SES — não duplica.

Isso cria: DynamoDB, SSM, SES (identidades), IAM (roles de execução das 3 funções, privilégio
mínimo), as 3 funções Lambda (com SnapStart + alias `live`), API Gateway (com JWT Authorizer),
Cognito (User Pool + client), EventBridge (barramento + regra + scheduler + DLQs) e CloudWatch
(dashboard + alarmes + SNS).

## 4. Passos manuais que a AWS exige (fora do Terraform)

Terraform cria os recursos, mas duas confirmações só chegam por e-mail e precisam ser clicadas:

1. **Verificação de identidade do SES** — chega um e-mail da AWS para cada identidade criada
   (remetente e, se for um e-mail diferente, o admin). Modo sandbox exige ambos verificados — sem
   confirmar, o envio de e-mail falha.
2. **Confirmação de inscrição do SNS** — chega um e-mail "AWS Notification - Subscription
   Confirmation" para o admin. Sem confirmar, os alarmes disparam mas o e-mail de alerta não
   chega.

## 5. Criar um usuário de teste no Cognito

`POST /avaliacao` exige um JWT válido (JWT Authorizer no API Gateway). Crie um usuário de teste:

```
USER_POOL_ID=$(terraform output -raw cognito_user_pool_id)
CLIENT_ID=$(terraform output -raw cognito_client_id)

aws cognito-idp admin-create-user \
  --user-pool-id "$USER_POOL_ID" --username estudante.teste --message-action SUPPRESS

aws cognito-idp admin-set-user-password \
  --user-pool-id "$USER_POOL_ID" --username estudante.teste --password "SenhaForte#2026" --permanent
```

A collection do Postman (pasta 00) faz exatamente isso automaticamente — ver `postman/README.md`.

## 6. Secret do GitHub Actions (CI)

Só um secret é necessário, independente de conta AWS — usado pelo workflow `CI` para rodar os
testes de integração contra LocalStack:

```
gh secret set LOCALSTACK_AUTH_TOKEN --body "<token de uma conta gratuita em app.localstack.cloud>"
```

Não há secret de credencial AWS no GitHub: o workflow `deploy.yml` só builda e empacota os jars
(não aplica infraestrutura).

## 7. Smoke test pós-deploy

```
API_URL=$(cd infra && terraform output -raw url_api)
CLIENT_ID=$(cd infra && terraform output -raw cognito_client_id)

ID_TOKEN=$(aws cognito-idp initiate-auth \
  --client-id "$CLIENT_ID" --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=estudante.teste,PASSWORD='SenhaForte#2026' \
  --query 'AuthenticationResult.IdToken' --output text)

curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ID_TOKEN" \
  -d '{"descricao": "Aula excelente, professor muito didático", "nota": 9}'

curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ID_TOKEN" \
  -d '{"descricao": "Aula com problemas graves de áudio", "nota": 1}'
```

Sem o header `Authorization`, a API responde `401` — é o comportamento esperado, não um erro.

- A primeira deve retornar `201` com `nivelUrgencia: BAIXA` e **não** gerar e-mail.
- A segunda deve retornar `201` com `nivelUrgencia: ALTA` e, em alguns segundos, chegar o e-mail
  de alerta (SES real, fora do sandbox do LocalStack).

Para testar o relatório semanal sem esperar a segunda-feira:

```
aws lambda invoke \
  --profile tech-challenge-fase04 \
  --function-name tech-challenge-fase04-gerar-relatorio-semanal \
  --payload '{}' \
  --cli-binary-format raw-in-base64-out \
  saida.json
cat saida.json
```

## Atualizações de código (deploy incremental)

Depois do setup inicial, qualquer mudança nas funções Lambda é aplicada rodando de novo, localmente:
`mvn package` gera os jars → `terraform plan`/`apply` detecta o novo `source_code_hash` (hash do
jar) → atualiza só a função que mudou. O workflow `deploy.yml` do GitHub Actions mantém os jars
sempre buildados automaticamente a cada push, prontos para esse `apply` local.

## Troubleshooting: apply interrompido (rede caiu, lock travado)

O `publish` das funções (necessário para o SnapStart) demora — a AWS precisa criar o snapshot
otimizado, não é só salvar o código. Isso aumenta a janela de exposição a uma queda de rede/wifi
no meio do `apply`. Já aconteceu em produção (2026-11-10): a conexão caiu durante o publish de uma
função, o `credential_process` (baseado em `aws configure export-credentials`) deu timeout, e o
Terraform não conseguiu nem salvar o state no S3 nem liberar o lock do DynamoDB.

**Recuperação segura, nessa ordem — nunca pule direto para `force-unlock`:**

1. Confirme que a rede voltou e a sessão ainda é válida: `aws sts get-caller-identity --profile
   tech-challenge-fase04`.
2. Confirme que o processo `terraform apply` original realmente morreu (`ps aux | grep terraform`)
   — nunca dar force-unlock com um apply genuíno ainda rodando em outro lugar.
3. Olhe o lock preso: `aws dynamodb scan --table-name tech-challenge-fase04-terraform-lock`. O
   `LockID` principal tem um `Info` com um ID de lock — é esse ID que vai no `force-unlock`.
4. Confira o real estado na AWS antes de confiar no Terraform (ex.: `aws lambda list-versions-by-function`
   para ver se uma nova versão foi publicada do lado do servidor mesmo com o cliente tendo caído —
   AWS continua processando operações já iniciadas mesmo que o Terraform perca a conexão).
5. Só então: `terraform force-unlock -force <LOCK_ID>`.
6. Rode `terraform plan` (nunca `apply` direto) — o Terraform detecta e avisa sobre mudanças feitas
   "outside of Terraform" (a versão publicada que o servidor completou), e o plano resultante
   normalmente é pequeno e seguro (só reconcilia o que ficou pela metade — ex.: apontar o alias
   `live` para a versão nova). Revise antes de aplicar.
7. Confirme consistência rodando `terraform plan` de novo depois do apply — deve dizer "No
   changes". Só então apague o `errored.tfstate` que o Terraform cria como backup local nesse tipo
   de falha.
