package br.com.fiap.techchallenge.feedback.aplicacao.porta;

import br.com.fiap.techchallenge.feedback.aplicacao.relatorio.RelatorioSemanal;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;

/**
 * Porta de envio de e-mails administrativos. A implementação concreta (ex.: SES)
 * fica no módulo de infraestrutura.
 */
public interface EnviadorEmail {

    void enviarAlertaUrgencia(Avaliacao avaliacao);

    void enviarRelatorioSemanal(RelatorioSemanal relatorio);
}
