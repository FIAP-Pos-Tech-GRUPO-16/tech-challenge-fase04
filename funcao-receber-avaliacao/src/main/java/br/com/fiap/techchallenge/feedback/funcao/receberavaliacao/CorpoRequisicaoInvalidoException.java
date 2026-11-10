package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

/**
 * Lançada quando o corpo da requisição HTTP está ausente ou malformado — um problema de
 * transporte/parsing, não uma violação de regra de negócio (essa é {@code AvaliacaoInvalidaException}).
 */
class CorpoRequisicaoInvalidoException extends RuntimeException {

    CorpoRequisicaoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
