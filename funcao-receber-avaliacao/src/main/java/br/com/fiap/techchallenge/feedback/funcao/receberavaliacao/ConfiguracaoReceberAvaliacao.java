package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.ReceberAvaliacaoCasoDeUso;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.PublicadorEvento;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.infraestrutura.aws.FabricaClientesAws;
import br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb.RepositorioAvaliacaoDynamoDb;
import br.com.fiap.techchallenge.feedback.infraestrutura.eventbridge.PublicadorEventoEventBridge;
import br.com.fiap.techchallenge.feedback.infraestrutura.snapstart.GanchoPosRestauracaoSnapStart;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
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
    FabricaClientesAws fabricaClientesAws() {
        return new FabricaClientesAws();
    }

    @Bean
    DynamoDbEnhancedClient clienteDynamoDbIntensificado(FabricaClientesAws fabrica) {
        return fabrica.dynamoDbIntensificado();
    }

    @Bean
    EventBridgeClient clienteEventBridge(FabricaClientesAws fabrica) {
        return fabrica.eventBridge();
    }

    // Gancho do AWS Lambda SnapStart: na restauração do snapshot, renova as credenciais da
    // execution role e aquece os clientes AWS SDK desta função (ver javadoc da classe).
    @Bean
    GanchoPosRestauracaoSnapStart ganchoPosRestauracaoSnapStart(FabricaClientesAws fabrica) {
        return fabrica.registrarGanchoSnapStart(DynamoDbClient.class, EventBridgeClient.class);
    }

    @Bean
    RepositorioAvaliacao repositorioAvaliacao(DynamoDbEnhancedClient clienteIntensificado) {
        String nomeTabela = variavelAmbiente("TABELA_AVALIACOES", "avaliacoes");
        return new RepositorioAvaliacaoDynamoDb(clienteIntensificado, nomeTabela);
    }

    @Bean
    PublicadorEvento publicadorEvento(EventBridgeClient clienteEventBridge, ObjectMapper mapeadorJson) {
        String nomeBarramento = variavelAmbiente("EVENTBRIDGE_BARRAMENTO", "default");
        String origem = variavelAmbiente("EVENTBRIDGE_ORIGEM", "tech-challenge.feedback");
        return new PublicadorEventoEventBridge(clienteEventBridge, nomeBarramento, origem, mapeadorJson);
    }

    // @Value("${...}") em parâmetros de método @Bean não é resolvido pelo bootstrap do
    // FunctionInvoker (Spring Cloud Function no Lambda) — lendo a variável de ambiente
    // diretamente, que é o mecanismo nativo de configuração do Lambda de qualquer forma.
    private static String variavelAmbiente(String nome, String valorPadrao) {
        String valor = System.getenv(nome);
        return valor != null ? valor : valorPadrao;
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
