package br.com.fiap.techchallenge.feedback.aplicacao.casodeuso;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.PublicadorEvento;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;

import java.time.Clock;
import java.time.Instant;

/**
 * Recebe uma avaliação enviada pelo estudante: valida, persiste e publica o evento
 * para que outros componentes (ex.: notificação de urgência) reajam de forma desacoplada.
 */
public class ReceberAvaliacaoCasoDeUso {

    private final RepositorioAvaliacao repositorioAvaliacao;
    private final PublicadorEvento publicadorEvento;
    private final Clock relogio;

    public ReceberAvaliacaoCasoDeUso(RepositorioAvaliacao repositorioAvaliacao, PublicadorEvento publicadorEvento, Clock relogio) {
        this.repositorioAvaliacao = repositorioAvaliacao;
        this.publicadorEvento = publicadorEvento;
        this.relogio = relogio;
    }

    public Avaliacao executar(String descricao, int nota) {
        Avaliacao avaliacao = Avaliacao.receber(descricao, nota, Instant.now(relogio));
        repositorioAvaliacao.salvar(avaliacao);
        publicadorEvento.publicarAvaliacaoRecebida(avaliacao);
        return avaliacao;
    }
}
