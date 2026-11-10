package br.com.fiap.techchallenge.feedback.infraestrutura.eventbridge;

/**
 * Lançada quando a publicação de um evento no EventBridge não é confirmada com sucesso.
 */
public class PublicacaoEventoFalhouException extends RuntimeException {

    public PublicacaoEventoFalhouException(String mensagem) {
        super(mensagem);
    }

    public PublicacaoEventoFalhouException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
