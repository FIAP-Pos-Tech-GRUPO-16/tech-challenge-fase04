package br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.GerarRelatorioSemanalCasoDeUso;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb.RepositorioAvaliacaoDynamoDb;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.EnviadorEmailSes;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.FornecedorEmailAdministrador;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.FornecedorEmailAdministradorSsm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    DynamoDbEnhancedClient clienteDynamoDbIntensificado() {
        DynamoDbClient clienteBaixoNivel = DynamoDbClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
        return DynamoDbEnhancedClient.builder().dynamoDbClient(clienteBaixoNivel).build();
    }

    @Bean
    SesV2Client clienteSes() {
        return SesV2Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    @Bean
    SsmClient clienteParametro() {
        return SsmClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    @Bean
    RepositorioAvaliacao repositorioAvaliacao(
        DynamoDbEnhancedClient clienteIntensificado,
        @Value("${app.dynamodb.tabela-avaliacoes}") String nomeTabela
    ) {
        return new RepositorioAvaliacaoDynamoDb(clienteIntensificado, nomeTabela);
    }

    @Bean
    FornecedorEmailAdministrador fornecedorEmailAdministrador(
        SsmClient clienteParametro,
        @Value("${app.ssm.parametro-email-admin}") String nomeParametro
    ) {
        return new FornecedorEmailAdministradorSsm(clienteParametro, nomeParametro);
    }

    @Bean
    EnviadorEmail enviadorEmail(
        SesV2Client clienteSes,
        FornecedorEmailAdministrador fornecedorEmailAdministrador,
        @Value("${app.ses.remetente}") String remetente
    ) {
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
}
