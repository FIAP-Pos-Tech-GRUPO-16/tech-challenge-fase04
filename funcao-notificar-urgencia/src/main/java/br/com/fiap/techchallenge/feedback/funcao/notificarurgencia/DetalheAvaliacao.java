package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

/**
 * Espelha o formato publicado por {@code PublicadorEventoEventBridge} (módulo de infraestrutura).
 */
record DetalheAvaliacao(String id, String descricao, int nota, String nivelUrgencia, String dataEnvio) {
}
