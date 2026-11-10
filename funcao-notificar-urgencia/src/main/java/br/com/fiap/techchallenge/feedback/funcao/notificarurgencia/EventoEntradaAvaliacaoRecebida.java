package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

/**
 * Envelope padrão do EventBridge: só mapeamos o campo {@code detail}, ignorando os demais
 * (version, id do evento, detail-type, source, time, region, resources) — por isso o
 * {@code ObjectMapper} usado aqui precisa tolerar propriedades desconhecidas.
 */
record EventoEntradaAvaliacaoRecebida(DetalheAvaliacao detail) {
}
