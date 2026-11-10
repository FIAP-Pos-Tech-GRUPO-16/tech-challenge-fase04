package br.com.fiap.techchallenge.feedback.infraestrutura.eventbridge;

/**
 * Formato de serialização do evento {@code AvaliacaoRecebida} publicado no EventBridge.
 * O campo {@code nivelUrgencia} já vem calculado pelo domínio e é usado pela regra do
 * EventBridge para rotear até a função de notificação de urgência.
 */
record AvaliacaoEventoPayload(String id, String descricao, int nota, String nivelUrgencia, String dataEnvio) {
}
