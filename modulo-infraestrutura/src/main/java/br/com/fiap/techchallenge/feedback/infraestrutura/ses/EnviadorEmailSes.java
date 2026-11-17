package br.com.fiap.techchallenge.feedback.infraestrutura.ses;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.aplicacao.relatorio.RelatorioSemanal;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.util.Locale;

/**
 * Implementação da porta {@link EnviadorEmail} usando o Amazon SES (API v2).
 */
public class EnviadorEmailSes implements EnviadorEmail {

    private static final Logger LOG = LoggerFactory.getLogger(EnviadorEmailSes.class);
    private static final Locale LOCALE_EMAIL = Locale.of("pt", "BR");

    private final SesV2Client clienteSes;
    private final FornecedorEmailAdministrador fornecedorEmailAdministrador;
    private final String remetente;

    public EnviadorEmailSes(SesV2Client clienteSes, FornecedorEmailAdministrador fornecedorEmailAdministrador, String remetente) {
        this.clienteSes = clienteSes;
        this.fornecedorEmailAdministrador = fornecedorEmailAdministrador;
        this.remetente = remetente;
    }

    @Override
    public void enviarAlertaUrgencia(Avaliacao avaliacao) {
        String idMensagem = enviar(
            "Alerta de urgência - feedback crítico recebido",
            montarCorpoAlerta(avaliacao)
        );

        LOG.atInfo()
            .addKeyValue("tipoEmail", "ALERTA_URGENCIA")
            .addKeyValue("avaliacaoId", avaliacao.id())
            .addKeyValue("sesMessageId", idMensagem)
            .log("E-mail de alerta de urgência enviado ao administrador");
    }

    @Override
    public void enviarRelatorioSemanal(RelatorioSemanal relatorio) {
        String idMensagem = enviar(
            "Relatório semanal de avaliações",
            montarCorpoRelatorio(relatorio)
        );

        LOG.atInfo()
            .addKeyValue("tipoEmail", "RELATORIO_SEMANAL")
            .addKeyValue("totalAvaliacoes", relatorio.avaliacoes().size())
            .addKeyValue("mediaNotas", relatorio.mediaNotas())
            .addKeyValue("sesMessageId", idMensagem)
            .log("E-mail do relatório semanal enviado ao administrador");
    }

    private String enviar(String assunto, String corpo) {
        String destinatario = fornecedorEmailAdministrador.obterEmailAdministrador();

        SendEmailRequest requisicao = SendEmailRequest.builder()
            .fromEmailAddress(remetente)
            .destination(destino -> destino.toAddresses(destinatario))
            .content(conteudo -> conteudo.simple(simples -> simples
                .subject(assuntoEmail -> assuntoEmail.data(assunto))
                .body(corpoEmail -> corpoEmail.text(texto -> texto.data(corpo)))))
            .build();

        return clienteSes.sendEmail(requisicao).messageId();
    }

    private String montarCorpoAlerta(Avaliacao avaliacao) {
        return """
            Uma avaliação crítica foi recebida.

            Descrição: %s
            Urgência: %s
            Data de envio: %s
            """.formatted(avaliacao.descricao(), avaliacao.nivelUrgencia(), avaliacao.dataEnvio());
    }

    private String montarCorpoRelatorio(RelatorioSemanal relatorio) {
        StringBuilder corpo = new StringBuilder();
        corpo.append("Relatório semanal de avaliações\n\n");
        corpo.append(String.format(LOCALE_EMAIL, "Média das notas: %.2f%n%n", relatorio.mediaNotas()));

        corpo.append("Quantidade de avaliações por dia:\n");
        relatorio.quantidadePorDia().forEach((dia, quantidade) ->
            corpo.append("  %s: %d\n".formatted(dia, quantidade)));

        corpo.append("\nQuantidade de avaliações por urgência:\n");
        relatorio.quantidadePorUrgencia().forEach((urgencia, quantidade) ->
            corpo.append("  %s: %d\n".formatted(urgencia, quantidade)));

        corpo.append("\nAvaliações do período:\n");
        relatorio.avaliacoes().forEach(avaliacao ->
            corpo.append("  - %s | %s | %s\n".formatted(avaliacao.dataEnvio(), avaliacao.nivelUrgencia(), avaliacao.descricao())));

        return corpo.toString();
    }
}
