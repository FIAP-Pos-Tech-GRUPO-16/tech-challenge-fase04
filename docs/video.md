# Roteiro de apoio para o vídeo de demonstração

O enunciado pede um vídeo mostrando a aplicação em funcionamento, as funções serverless ativas e
as configurações do projeto. Sugestão de roteiro (ajustar à vontade):

## 1. Arquitetura (1-2 min)
- Abrir [`arquitetura.md`](arquitetura.md) e mostrar o diagrama.
- Explicar rapidamente por que AWS + serverless e como as 3 funções se encaixam (cada uma com
  responsabilidade única).

## 2. Infraestrutura provisionada (2-3 min)
- Console AWS → Lambda: mostrar as 3 funções ativas (`receber-avaliacao`, `notificar-urgencia`,
  `gerar-relatorio-semanal`).
- Console AWS → API Gateway: mostrar a rota `POST /avaliacao`.
- Console AWS → EventBridge: mostrar o barramento, a regra de urgência e o schedule semanal.
- Console AWS → DynamoDB: mostrar a tabela `avaliacoes` e o índice `gsi_data_envio`.
- Console AWS → IAM: mostrar pelo menos uma role de execução e sua política de privilégio mínimo.

## 3. Funcionamento (3-4 min)
- Enviar uma avaliação com nota alta via `curl` (ver [`deploy.md`](deploy.md)) → mostrar o `201`
  e o item salvo no DynamoDB.
- Enviar uma avaliação com nota baixa (urgência ALTA) → mostrar o e-mail de alerta chegando.
- Invocar manualmente `gerar-relatorio-semanal` → mostrar o e-mail do relatório chegando.

## 4. Monitoramento (1-2 min)
- Abrir o dashboard CloudWatch (`terraform output url_dashboard`) e mostrar os widgets com dados
  reais das invocações feitas no passo anterior.
- Mostrar um dos alarmes configurados (mesmo que em estado OK).

## 5. Build e testes automatizados (1-2 min)
- Mostrar o `ci.yml` (testes unitários + integração + validação do Terraform) rodando no GitHub
  Actions a cada push.
- Mostrar o pipeline `tech-challenge-fase04-deploy` no CodePipeline: o push na `main` dispara o
  CodeBuild, que roda os testes, empacota as funções e faz o `terraform apply` sozinho.
- Explicar por que o deploy roda dentro da AWS: a "nova experiência AWS" bloqueia o provedor OIDC
  do GitHub Actions, e a alternativa (access key no GitHub) seria menos segura. Com
  CodeConnections, nenhuma credencial AWS fica no GitHub.

## Links do console AWS para mostrar no vídeo

Entre antes no console com a conta do projeto (região `us-east-2`). Os links abaixo usam o endereço
padrão do console; a nova experiência AWS redireciona para o subdomínio da conta automaticamente.

**Segurança e governança de acesso**
- Roles IAM (uma por função, privilégio mínimo):
  https://us-east-1.console.aws.amazon.com/iam/home#/roles?search=tech-challenge-fase04
- Exemplo de policy mínima (`notificar-urgencia`):
  https://us-east-1.console.aws.amazon.com/iam/home#/roles/details/tech-challenge-fase04-notificar-urgencia?section=permissions
- Cognito (User Pool, cadastro só pelo admin, client sem secret):
  https://us-east-2.console.aws.amazon.com/cognito/v2/idp/user-pools/us-east-2_rGGDsOCdY/overview?region=us-east-2
- API Gateway (rota `POST /avaliacao` com JWT Authorizer, throttling e log de acesso):
  https://us-east-2.console.aws.amazon.com/apigateway/main/develop/routes?api=0wywnn2xkg&region=us-east-2
- DynamoDB (criptografia e PITR em *Backups* / *Additional settings*):
  https://us-east-2.console.aws.amazon.com/dynamodbv2/home?region=us-east-2#table?name=tech-challenge-fase04-avaliacoes
- DLQs SQS (criptografia SSE):
  https://us-east-2.console.aws.amazon.com/sqs/v3/home?region=us-east-2#/queues
- SSM Parameter Store (e-mail do admin fora do código):
  https://us-east-2.console.aws.amazon.com/systems-manager/parameters?region=us-east-2
- SES (identidades verificadas, modo sandbox):
  https://us-east-2.console.aws.amazon.com/ses/home?region=us-east-2#/identities
- Bucket do state do Terraform (acesso público bloqueado, criptografia, versionamento):
  https://us-east-1.console.aws.amazon.com/s3/buckets/tech-challenge-fase04-terraform-state-827938108008?region=us-east-2&tab=permissions
- Auditoria (histórico de eventos do CloudTrail):
  https://us-east-2.console.aws.amazon.com/cloudtrail/home?region=us-east-2#/events
- Gestão do projeto e políticas da conta (nova experiência AWS): https://settings.aws.com

**Funções, deploy e monitoramento**
- Lambdas: https://us-east-2.console.aws.amazon.com/lambda/home?region=us-east-2#/functions
- Pipeline de deploy:
  https://us-east-2.console.aws.amazon.com/codesuite/codepipeline/pipelines/tech-challenge-fase04-deploy/view?region=us-east-2
- EventBridge (barramento, regra e agendamento):
  https://us-east-2.console.aws.amazon.com/events/home?region=us-east-2#/eventbuses
  e https://us-east-2.console.aws.amazon.com/scheduler/home?region=us-east-2#schedules
- Dashboard:
  https://us-east-2.console.aws.amazon.com/cloudwatch/home?region=us-east-2#dashboards/dashboard/tech-challenge-fase04-visao-geral
- Alarmes: https://us-east-2.console.aws.amazon.com/cloudwatch/home?region=us-east-2#alarmsV2:
- Logs Insights: https://us-east-2.console.aws.amazon.com/cloudwatch/home?region=us-east-2#logsV2:logs-insights

## Checklist antes de gravar
- [ ] Repositório com todo o código commitado e enviado.
- [ ] Última execução do pipeline `tech-challenge-fase04-deploy` com sucesso.
- [ ] SES e SNS com os e-mails de verificação/confirmação já clicados.
- [ ] Pelo menos uma avaliação crítica e uma não crítica já enviadas, para o dashboard não
  aparecer vazio.
- [ ] `terraform output` à mão para copiar URLs durante a gravação.
