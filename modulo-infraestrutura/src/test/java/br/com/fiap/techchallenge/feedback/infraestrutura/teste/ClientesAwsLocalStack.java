package br.com.fiap.techchallenge.feedback.infraestrutura.teste;

import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Credenciais e região fictícias do LocalStack, compartilhadas pelos testes de integração
 * dos adapters de infraestrutura.
 */
public final class ClientesAwsLocalStack {

    private ClientesAwsLocalStack() {
    }

    public static AwsCredentialsProvider credenciais(LocalStackContainer localstack) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
    }

    public static Region regiao(LocalStackContainer localstack) {
        return Region.of(localstack.getRegion());
    }

    /**
     * Desde março de 2026 o LocalStack exige uma conta gratuita e um token de autenticação
     * para iniciar, mesmo para serviços community (DynamoDB, EventBridge). O token é lido do
     * ambiente (variável {@code LOCALSTACK_AUTH_TOKEN}) e nunca deve ser commitado — veja o
     * arquivo {@code .env} (ignorado pelo git) na raiz do projeto.
     */
    public static String tokenAutenticacao() {
        String token = System.getenv("LOCALSTACK_AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "LOCALSTACK_AUTH_TOKEN não configurado. Exporte a variável (veja o .env local) antes de rodar os testes de integração.");
        }
        return token;
    }
}
