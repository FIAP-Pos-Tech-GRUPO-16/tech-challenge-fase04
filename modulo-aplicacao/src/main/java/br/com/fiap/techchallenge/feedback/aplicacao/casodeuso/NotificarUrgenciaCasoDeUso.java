package br.com.fiap.techchallenge.feedback.aplicacao.casodeuso;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;

/**
 * Notifica o administrador por e-mail quando uma avaliação é crítica (urgência ALTA).
 * O roteamento até esta função já é filtrado na infraestrutura (regra do EventBridge),
 * mas a verificação aqui garante que a regra de negócio nunca seja violada mesmo que a
 * função seja invocada diretamente.
 */
public class NotificarUrgenciaCasoDeUso {

    private final EnviadorEmail enviadorEmail;

    public NotificarUrgenciaCasoDeUso(EnviadorEmail enviadorEmail) {
        this.enviadorEmail = enviadorEmail;
    }

    public void executar(Avaliacao avaliacao) {
        if (!avaliacao.isCritica()) {
            return;
        }
        enviadorEmail.enviarAlertaUrgencia(avaliacao);
    }
}
