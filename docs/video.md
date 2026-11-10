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
- Mostrar os pipelines `ci.yml` (testes unitários + integração + validação do Terraform) e
  `deploy.yml` (empacotamento dos jars) rodando no GitHub Actions a cada push.
- Explicar que o `terraform apply` é manual, feito localmente por quem tem acesso à conta AWS —
  a conta usa a "nova experiência AWS", que bloqueia a criação de um provedor OIDC por política
  de organização, então o CI não guarda nenhuma credencial AWS.

## Checklist antes de gravar
- [ ] Repositório com todo o código commitado e enviado.
- [ ] Infraestrutura aplicada na AWS (passo manual, ver `deploy.md`).
- [ ] SES e SNS com os e-mails de verificação/confirmação já clicados.
- [ ] Pelo menos uma avaliação crítica e uma não crítica já enviadas, para o dashboard não
  aparecer vazio.
- [ ] `terraform output` à mão para copiar URLs durante a gravação.
