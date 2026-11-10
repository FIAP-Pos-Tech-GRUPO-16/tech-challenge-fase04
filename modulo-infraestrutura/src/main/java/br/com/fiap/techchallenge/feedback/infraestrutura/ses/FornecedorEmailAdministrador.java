package br.com.fiap.techchallenge.feedback.infraestrutura.ses;

/**
 * Resolve o e-mail do administrador que deve receber os alertas e relatórios.
 * Mantido como abstração própria (e não hardcoded) porque o destinatário muda
 * sem exigir novo deploy de código.
 */
public interface FornecedorEmailAdministrador {

    String obterEmailAdministrador();
}
