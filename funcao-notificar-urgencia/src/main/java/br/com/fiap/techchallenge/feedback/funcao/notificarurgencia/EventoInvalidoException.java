package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

/**
 * Lançada quando o evento recebido do EventBridge não pode ser interpretado. Propositalmente
 * não é capturada no handler: deixar a invocação falhar aciona o retry/DLQ configurado no
 * EventBridge, em vez de descartar silenciosamente um evento que não pôde ser processado.
 */
class EventoInvalidoException extends RuntimeException {

    EventoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
