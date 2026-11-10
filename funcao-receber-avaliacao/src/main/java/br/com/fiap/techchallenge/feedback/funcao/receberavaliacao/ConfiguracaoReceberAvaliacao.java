package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.ReceberAvaliacaoCasoDeUso;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.PublicadorEvento;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb.RepositorioAvaliacaoDynamoDb;
import br.com.fiap.techchallenge.feedback.infraestrutura.eventbridge.PublicadorEventoEventBridge;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.time.Clock;
import java.util.function.Function;

/**
 * Composição (DI manual) da função: constrói os clientes AWS uma única vez — reaproveitados
 * entre invocações quentes do Lambda — e liga o handler ao caso de uso.
 */
@Configuration
class ConfiguracaoReceberAvaliacao {

    @Bean
    ObjectMapper mapeadorJson() {
        return new ObjectMapper();
    }

    @Bean
    DynamoDbEnhancedClient clienteDynamoDbIntensificado() {
        DynamoDbClient clienteBaixoNivel = DynamoDbClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
        return DynamoDbEnhancedClient.builder().dynamoDbClient(clienteBaixoNivel).build();
    }

    @Bean
    EventBridgeClient clienteEventBridge() {
        return EventBridgeClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }

    @Bean
    RepositorioAvaliacao repositorioAvaliacao(
        DynamoDbEnhancedClient clienteIntensificado,
        @Value("${app.dynamodb.tabela-avaliacoes}") String nomeTabela
    ) {
        return new RepositorioAvaliacaoDynamoDb(clienteIntensificado, nomeTabela);
    }

    @Bean
    PublicadorEvento publicadorEvento(
        EventBridgeClient clienteEventBridge,
        ObjectMapper mapeadorJson,
        @Value("${app.eventbridge.nome-barramento}") String nomeBarramento,
        @Value("${app.eventbridge.origem}") String origem
    ) {
        return new PublicadorEventoEventBridge(clienteEventBridge, nomeBarramento, origem, mapeadorJson);
    }

    @Bean
    ReceberAvaliacaoCasoDeUso receberAvaliacaoCasoDeUso(RepositorioAvaliacao repositorio, PublicadorEvento publicadorEvento) {
        return new ReceberAvaliacaoCasoDeUso(repositorio, publicadorEvento, Clock.systemUTC());
    }

    @Bean
    Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> receberAvaliacao(ReceberAvaliacaoCasoDeUso casoDeUso, ObjectMapper mapeadorJson) {
        return new HandlerReceberAvaliacao(casoDeUso, mapeadorJson);
    }
}
