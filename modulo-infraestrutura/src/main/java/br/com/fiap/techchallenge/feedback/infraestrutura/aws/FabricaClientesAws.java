package br.com.fiap.techchallenge.feedback.infraestrutura.aws;

import br.com.fiap.techchallenge.feedback.infraestrutura.snapstart.GanchoPosRestauracaoSnapStart;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Constrói os clientes AWS SDK usados pelas funções Lambda, todos compartilhando o mesmo
 * {@link AwsCredentialsProvider} e o mesmo tipo de HTTP client — centraliza o que antes era
 * repetido de forma idêntica nas 3 classes {@code Configuracao*} (uma por função).
 *
 * <p>Cada função instancia uma única {@code FabricaClientesAws} e usa só os métodos dos
 * clientes que realmente precisa.
 */
public final class FabricaClientesAws {

    private final AwsCredentialsProvider provedorCredenciais;

    public FabricaClientesAws() {
        this.provedorCredenciais = DefaultCredentialsProvider.create();
    }

    public AwsCredentialsProvider provedorCredenciais() {
        return provedorCredenciais;
    }

    public DynamoDbEnhancedClient dynamoDbIntensificado() {
        DynamoDbClient clienteBaixoNivel = DynamoDbClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
        return DynamoDbEnhancedClient.builder().dynamoDbClient(clienteBaixoNivel).build();
    }

    public SesV2Client ses() {
        return SesV2Client.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
    }

    public EventBridgeClient eventBridge() {
        return EventBridgeClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
    }

    public SsmClient ssm() {
        return SsmClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
    }

    /**
     * Registra o gancho de SnapStart desta função, aquecendo exatamente os clientes informados.
     */
    @SafeVarargs
    public final GanchoPosRestauracaoSnapStart registrarGanchoSnapStart(Class<? extends SdkClient>... clientesParaAquecer) {
        return new GanchoPosRestauracaoSnapStart(provedorCredenciais, clientesParaAquecer);
    }
}
