package br.com.fiap.techchallenge.feedback.dominio;

/**
 * Lançada quando os dados de uma avaliação violam as regras de negócio do domínio.
 */
public class AvaliacaoInvalidaException extends RuntimeException {

    public AvaliacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
