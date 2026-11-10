package br.com.fiap.techchallenge.feedback.aplicacao.porta;

import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;

/**
 * Porta de publicação de eventos de domínio. A implementação concreta (ex.: EventBridge)
 * fica no módulo de infraestrutura e é responsável apenas por publicar — o roteamento até
 * quem consome (ex.: notificação de urgência) é uma decisão de infraestrutura.
 */
public interface PublicadorEvento {

    void publicarAvaliacaoRecebida(Avaliacao avaliacao);
}
