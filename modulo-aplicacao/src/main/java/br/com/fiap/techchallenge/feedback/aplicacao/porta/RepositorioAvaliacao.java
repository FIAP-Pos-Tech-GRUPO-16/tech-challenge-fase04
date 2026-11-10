package br.com.fiap.techchallenge.feedback.aplicacao.porta;

import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;

import java.time.Instant;
import java.util.List;

/**
 * Porta de persistência das avaliações. A implementação concreta (ex.: DynamoDB)
 * fica no módulo de infraestrutura.
 */
public interface RepositorioAvaliacao {

    void salvar(Avaliacao avaliacao);

    List<Avaliacao> buscarPorPeriodo(Instant inicio, Instant fim);
}
