# Instruções de deploy

O deploy tem duas partes: um **primeiro apply manual** (alguém do grupo, com uma conta AWS e
credenciais locais) e, a partir daí, **deploys automatizados** via GitHub Actions usando OIDC —
sem access keys guardadas como secret.

## 0. Pré-requisitos

- Conta AWS (camada gratuita é suficiente para este desafio).
- Usuário IAM com permissão de administrador **só para este primeiro setup** (depois disso, o
  deploy contínuo roda com a role de escopo limitado criada pelo próprio Terraform).
- `aws` CLI, `terraform` (>= 1.9) e Java 21 instalados localmente.
- Build das 3 funções feito (`mvn -DskipTests package` na raiz) — o Terraform referencia os jars
  em `funcao-*/target/*.jar`.

## 1. Configurar credenciais AWS localmente

```
aws configure
```
Informe access key, secret key e a região (`us-east-1`, igual ao padrão do projeto).

## 2. Bootstrap do state remoto (uma única vez)

```
cd infra/bootstrap
terraform init
terraform apply
```

Cria o bucket S3 (`tech-challenge-fase04-terraform-state`) e a tabela DynamoDB
(`tech-challenge-fase04-terraform-lock`) usados pelo backend remoto do módulo principal. Os nomes
já estão fixados em `infra/backend.hcl` — se mudar o prefixo do projeto, atualize os dois lugares.

## 3. Primeiro apply do módulo principal

```
cd infra
mvn -DskipTests package -f ..   # garante que os 3 jars existem antes do apply
terraform init -backend-config=backend.hcl
terraform plan \
  -var="email_admin_inicial=<email-do-admin>" \
  -var="email_remetente=<email-remetente-verificavel>"
terraform apply \
  -var="email_admin_inicial=<email-do-admin>" \
  -var="email_remetente=<email-remetente-verificavel>"
```

Isso cria: DynamoDB, SSM, SES (identidades), IAM (roles + provider OIDC do GitHub), as 3 funções
Lambda, API Gateway, EventBridge (barramento + regra + scheduler + DLQs) e CloudWatch (dashboard +
alarmes + SNS).

## 4. Passos manuais que a AWS exige (fora do Terraform)

Terraform cria os recursos, mas duas confirmações só chegam por e-mail e precisam ser clicadas:

1. **Verificação de identidade do SES** — chega um e-mail da AWS tanto para o remetente quanto
   para o e-mail do admin (modo sandbox exige os dois verificados). Sem confirmar, o envio de
   e-mail falha.
2. **Confirmação de inscrição do SNS** — chega um e-mail "AWS Notification - Subscription
   Confirmation" para o admin. Sem confirmar, os alarmes disparam mas o e-mail de alerta não
   chega.

## 5. Configurar o deploy automatizado (GitHub Actions)

Depois do primeiro `apply`, pegue a role de deploy criada:

```
terraform output arn_role_deploy_github_actions
```

No repositório GitHub, em **Settings → Secrets and variables → Actions**, cadastre:

| Secret | Valor |
|---|---|
| `AWS_ROLE_ARN` | saída do comando acima |
| `EMAIL_ADMIN_INICIAL` | mesmo e-mail usado no apply manual |
| `EMAIL_REMETENTE` | mesmo e-mail remetente usado no apply manual |
| `LOCALSTACK_AUTH_TOKEN` | token de uma conta gratuita em app.localstack.cloud (só para os testes de integração do CI) |

A partir daqui, todo push em `main` builda os 3 jars e roda `terraform plan` automaticamente
(`.github/workflows/deploy.yml`). O `terraform apply` **não roda sozinho** — é preciso disparar o
workflow manualmente (aba Actions → Deploy → Run workflow, marcando a opção "aplicar"), para não
gastar créditos de cloud em pushes não intencionais.

## 6. Smoke test pós-deploy

```
API_URL=$(cd infra && terraform output -raw url_api)

curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -d '{"descricao": "Aula excelente, professor muito didático", "nota": 9}'

curl -i -X POST "$API_URL/avaliacao" \
  -H "Content-Type: application/json" \
  -d '{"descricao": "Aula com problemas graves de áudio", "nota": 1}'
```

- A primeira deve retornar `201` com `nivelUrgencia: BAIXA` e **não** gerar e-mail.
- A segunda deve retornar `201` com `nivelUrgencia: ALTA` e, em alguns segundos, chegar o e-mail
  de alerta (SES real, fora do sandbox do LocalStack).

Para testar o relatório semanal sem esperar a segunda-feira:

```
aws lambda invoke \
  --function-name tech-challenge-fase04-gerar-relatorio-semanal \
  --payload '{}' \
  --cli-binary-format raw-in-base64-out \
  saida.json
cat saida.json
```

## Atualizações de código (deploy incremental)

Depois do setup inicial, qualquer mudança nas funções Lambda é aplicada automaticamente pelo
pipeline: `mvn package` gera os jars → `terraform apply` detecta o novo
`source_code_hash` (hash do jar) → atualiza só a função que mudou.
