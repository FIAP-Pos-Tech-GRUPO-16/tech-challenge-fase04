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

## 5. Deploy automatizado (1-2 min)
- Mostrar o pipeline `.github/workflows/deploy.yml` rodando no GitHub Actions (aba Actions do
  repositório) — build dos jars + `terraform plan`.
- Explicar que o `apply` é disparado manualmente (`workflow_dispatch`) para controlar o consumo de
  créditos de cloud, e que a autenticação é via OIDC (sem access key guardada no repositório).

## Checklist antes de gravar
- [ ] Repositório com todo o código commitado e enviado.
- [ ] Infraestrutura aplicada na AWS (passo manual, ver `deploy.md`).
- [ ] SES e SNS com os e-mails de verificação/confirmação já clicados.
- [ ] Pelo menos uma avaliação crítica e uma não crítica já enviadas, para o dashboard não
  aparecer vazio.
- [ ] `terraform output` à mão para copiar URLs durante a gravação.
