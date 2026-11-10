package br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.GerarRelatorioSemanalCasoDeUso;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb.RepositorioAvaliacaoDynamoDb;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.EnviadorEmailSes;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.FornecedorEmailAdministrador;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.FornecedorEmailAdministradorSsm;
import br.com.fiap.techchallenge.feedback.infraestrutura.snapstart.GanchoPosRestauracaoSnapStart;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.time.Clock;
import java.util.Map;
import java.util.function.Function;

/**
 * Composição (DI manual) da função: constrói os clientes AWS uma única vez — reaproveitados
 * entre invocações quentes do Lambda — e liga o handler ao caso de uso.
 */
@Configuration
class ConfiguracaoGerarRelatorioSemanal {

    @Bean
    AwsCredentialsProvider provedorCredenciais() {
        return DefaultCredentialsProvider.create();
    }

    @Bean
    DynamoDbEnhancedClient clienteDynamoDbIntensificado(AwsCredentialsProvider provedorCredenciais) {
        DynamoDbClient clienteBaixoNivel = DynamoDbClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
        return DynamoDbEnhancedClient.builder().dynamoDbClient(clienteBaixoNivel).build();
    }

    @Bean
    SesV2Client clienteSes(AwsCredentialsProvider provedorCredenciais) {
        return SesV2Client.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
    }

    @Bean
    SsmClient clienteParametro(AwsCredentialsProvider provedorCredenciais) {
        return SsmClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(provedorCredenciais)
            .build();
    }

    // Gancho do AWS Lambda SnapStart: na restauração do snapshot, renova as credenciais da
    // execution role e aquece os clientes AWS SDK desta função (ver javadoc da classe).
    @Bean
    GanchoPosRestauracaoSnapStart ganchoPosRestauracaoSnapStart(AwsCredentialsProvider provedorCredenciais) {
        return new GanchoPosRestauracaoSnapStart(provedorCredenciais, DynamoDbClient.class, SesV2Client.class, SsmClient.class);
    }

    @Bean
    RepositorioAvaliacao repositorioAvaliacao(DynamoDbEnhancedClient clienteIntensificado) {
        String nomeTabela = variavelAmbiente("TABELA_AVALIACOES", "avaliacoes");
        return new RepositorioAvaliacaoDynamoDb(clienteIntensificado, nomeTabela);
    }

    @Bean
    FornecedorEmailAdministrador fornecedorEmailAdministrador(SsmClient clienteParametro) {
        String nomeParametro = variavelAmbiente("SSM_PARAMETRO_EMAIL_ADMIN", "/tech-challenge-fase04/email-admin");
        return new FornecedorEmailAdministradorSsm(clienteParametro, nomeParametro);
    }

    @Bean
    EnviadorEmail enviadorEmail(SesV2Client clienteSes, FornecedorEmailAdministrador fornecedorEmailAdministrador) {
        String remetente = variavelAmbiente("SES_REMETENTE", "no-reply@tech-challenge-fase04.fiap.com.br");
        return new EnviadorEmailSes(clienteSes, fornecedorEmailAdministrador, remetente);
    }

    @Bean
    GerarRelatorioSemanalCasoDeUso gerarRelatorioSemanalCasoDeUso(RepositorioAvaliacao repositorio, EnviadorEmail enviadorEmail) {
        return new GerarRelatorioSemanalCasoDeUso(repositorio, enviadorEmail, Clock.systemUTC());
    }

    @Bean
    Function<Map<String, Object>, String> gerarRelatorioSemanal(GerarRelatorioSemanalCasoDeUso casoDeUso) {
        return new HandlerGerarRelatorioSemanal(casoDeUso);
    }

    // @Value("${...}") em parâmetros de método @Bean não é resolvido pelo bootstrap do
    // FunctionInvoker (Spring Cloud Function no Lambda) — lendo a variável de ambiente
    // diretamente, que é o mecanismo nativo de configuração do Lambda de qualquer forma.
    private static String variavelAmbiente(String nome, String valorPadrao) {
        String valor = System.getenv(nome);
        return valor != null ? valor : valorPadrao;
    }
}
