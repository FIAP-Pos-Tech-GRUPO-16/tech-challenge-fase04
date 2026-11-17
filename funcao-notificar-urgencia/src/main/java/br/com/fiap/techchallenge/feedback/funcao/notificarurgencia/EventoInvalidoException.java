package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

/**
 * Lançada quando o evento recebido do EventBridge não pode ser interpretado. Propositalmente
 * não é capturada no handler: como o EventBridge invoca a função de forma assíncrona, deixar a
 * invocação falhar aciona as novas tentativas da própria Lambda e, esgotadas, o destino de falha
 * (on-failure) configurado no alias, que grava o evento na DLQ em vez de descartá-lo.
 */
class EventoInvalidoException extends RuntimeException {

    EventoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
