package br.com.fiap.techchallenge.feedback.infraestrutura.ses;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.aplicacao.relatorio.RelatorioSemanal;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.util.Locale;

/**
 * Implementação da porta {@link EnviadorEmail} usando o Amazon SES (API v2).
 */
public class EnviadorEmailSes implements EnviadorEmail {

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
        enviar(
            "Alerta de urgência - feedback crítico recebido",
            montarCorpoAlerta(avaliacao)
        );
    }

    @Override
    public void enviarRelatorioSemanal(RelatorioSemanal relatorio) {
        enviar(
            "Relatório semanal de avaliações",
            montarCorpoRelatorio(relatorio)
        );
    }

    private void enviar(String assunto, String corpo) {
        String destinatario = fornecedorEmailAdministrador.obterEmailAdministrador();

        SendEmailRequest requisicao = SendEmailRequest.builder()
            .fromEmailAddress(remetente)
            .destination(destino -> destino.toAddresses(destinatario))
            .content(conteudo -> conteudo.simple(simples -> simples
                .subject(assuntoEmail -> assuntoEmail.data(assunto))
                .body(corpoEmail -> corpoEmail.text(texto -> texto.data(corpo)))))
            .build();

        clienteSes.sendEmail(requisicao);
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
