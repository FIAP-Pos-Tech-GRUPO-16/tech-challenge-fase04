package br.com.fiap.techchallenge.feedback.infraestrutura.ses;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

/**
 * Lê o e-mail do administrador de um parâmetro no SSM Parameter Store a cada chamada,
 * garantindo que uma troca de destinatário tenha efeito imediato, sem redeploy.
 */
public class FornecedorEmailAdministradorSsm implements FornecedorEmailAdministrador {

    private final SsmClient clienteParametro;
    private final String nomeParametro;

    public FornecedorEmailAdministradorSsm(SsmClient clienteParametro, String nomeParametro) {
        this.clienteParametro = clienteParametro;
        this.nomeParametro = nomeParametro;
    }

    @Override
    public String obterEmailAdministrador() {
        GetParameterRequest requisicao = GetParameterRequest.builder()
            .name(nomeParametro)
            .withDecryption(true)
            .build();

        return clienteParametro.getParameter(requisicao).parameter().value();
    }
}
