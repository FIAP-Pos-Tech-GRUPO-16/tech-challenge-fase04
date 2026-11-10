package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

/**
 * Corpo esperado em {@code POST /avaliacao}, conforme o contrato do desafio.
 * {@code nota} fica como {@link Integer} (não {@code int}) para que a ausência do campo
 * no JSON seja detectável como {@code null} em vez de virar silenciosamente zero.
 */
record RequisicaoAvaliacao(String descricao, Integer nota) {
}
