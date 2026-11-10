package br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb;

import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;
import br.com.fiap.techchallenge.feedback.infraestrutura.teste.ClientesAwsLocalStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RepositorioAvaliacaoDynamoDbIT {

    private static final String NOME_TABELA = "avaliacoes";

    @Container
    static final LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2026.10.6"))
        .withServices(LocalStackContainer.Service.DYNAMODB)
        .withEnv("LOCALSTACK_AUTH_TOKEN", ClientesAwsLocalStack.tokenAutenticacao());

    private static RepositorioAvaliacaoDynamoDb repositorio;

    @BeforeAll
    static void configurar() {
        DynamoDbClient clienteBaixoNivel = DynamoDbClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
            .region(ClientesAwsLocalStack.regiao(localstack))
            .credentialsProvider(ClientesAwsLocalStack.credenciais(localstack))
            .build();

        criarTabela(clienteBaixoNivel);

        DynamoDbEnhancedClient clienteIntensificado = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(clienteBaixoNivel)
            .build();

        repositorio = new RepositorioAvaliacaoDynamoDb(clienteIntensificado, NOME_TABELA);
    }

    private static void criarTabela(DynamoDbClient cliente) {
        cliente.createTable(CreateTableRequest.builder()
            .tableName(NOME_TABELA)
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .attributeDefinitions(
                AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("tipo").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("dataEnvio").attributeType(ScalarAttributeType.S).build()
            )
            .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
            .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                .indexName(AvaliacaoItemDynamoDb.NOME_INDICE_POR_DATA)
                .keySchema(
                    KeySchemaElement.builder().attributeName("tipo").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("dataEnvio").keyType(KeyType.RANGE).build()
                )
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build())
            .build());
    }

    @Test
    void deveSalvarEBuscarApenasAsAvaliacoesDentroDoPeriodoInformado() {
        Instant agora = Instant.now();
        Avaliacao avaliacaoDentroPeriodo = new Avaliacao(UUID.randomUUID(), "Aula dentro do período", 7, NivelUrgencia.BAIXA, agora);
        Avaliacao avaliacaoForaPeriodo = new Avaliacao(UUID.randomUUID(), "Aula fora do período", 7, NivelUrgencia.BAIXA, agora.minus(30, ChronoUnit.DAYS));

        repositorio.salvar(avaliacaoDentroPeriodo);
        repositorio.salvar(avaliacaoForaPeriodo);

        List<Avaliacao> resultado = repositorio.buscarPorPeriodo(agora.minus(1, ChronoUnit.DAYS), agora.plus(1, ChronoUnit.DAYS));

        assertThat(resultado).containsExactly(avaliacaoDentroPeriodo);
    }
}
