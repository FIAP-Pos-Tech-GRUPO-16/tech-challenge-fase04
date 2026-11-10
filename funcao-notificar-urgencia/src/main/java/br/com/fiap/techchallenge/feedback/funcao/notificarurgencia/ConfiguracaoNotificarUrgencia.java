package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.NotificarUrgenciaCasoDeUso;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.EnviadorEmailSes;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.FornecedorEmailAdministrador;
import br.com.fiap.techchallenge.feedback.infraestrutura.ses.FornecedorEmailAdministradorSsm;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.util.function.Function;

/**
 * Composição (DI manual) da função: constrói os clientes AWS uma única vez — reaproveitados
 * entre invocações quentes do Lambda — e liga o handler ao caso de uso.
 */
@Configuration
class ConfiguracaoNotificarUrgencia {

    @Bean
    ObjectMapper mapeadorJson() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
    NotificarUrgenciaCasoDeUso notificarUrgenciaCasoDeUso(EnviadorEmail enviadorEmail) {
        return new NotificarUrgenciaCasoDeUso(enviadorEmail);
    }

    @Bean
    Function<String, String> notificarUrgencia(NotificarUrgenciaCasoDeUso casoDeUso, ObjectMapper mapeadorJson) {
        return new HandlerNotificarUrgencia(casoDeUso, mapeadorJson);
    }
}
