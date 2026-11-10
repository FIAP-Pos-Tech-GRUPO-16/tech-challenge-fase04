package br.com.fiap.techchallenge.feedback.infraestrutura.eventbridge;

import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.infraestrutura.teste.ClientesAwsLocalStack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
class PublicadorEventoEventBridgeIT {

    private static final String NOME_BARRAMENTO = "default";
    private static final String ORIGEM = "tech-challenge.feedback";
    private static final LocalStackContainer.EnabledService EVENTBRIDGE = LocalStackContainer.EnabledService.named("events");

    @Container
    static final LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2026.10.6"))
        .withServices(EVENTBRIDGE)
        .withEnv("LOCALSTACK_AUTH_TOKEN", ClientesAwsLocalStack.tokenAutenticacao());

    private static PublicadorEventoEventBridge publicador;

    @BeforeAll
    static void configurar() {
        EventBridgeClient clienteEventBridge = EventBridgeClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .endpointOverride(localstack.getEndpointOverride(EVENTBRIDGE))
            .region(ClientesAwsLocalStack.regiao(localstack))
            .credentialsProvider(ClientesAwsLocalStack.credenciais(localstack))
            .build();

        publicador = new PublicadorEventoEventBridge(clienteEventBridge, NOME_BARRAMENTO, ORIGEM, new ObjectMapper());
    }

    @Test
    void devePublicarEventoAvaliacaoRecebidaSemErros() {
        Avaliacao avaliacao = Avaliacao.receber("Aula com problema de conexão", 2, Instant.now());

        assertThatCode(() -> publicador.publicarAvaliacaoRecebida(avaliacao)).doesNotThrowAnyException();
    }
}
