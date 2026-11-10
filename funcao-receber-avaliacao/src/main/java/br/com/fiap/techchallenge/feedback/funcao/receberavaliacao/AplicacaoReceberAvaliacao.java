package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada Spring Boot. A classe efetivamente invocada pelo Lambda é
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker}; esta classe é
 * referenciada pela variável de ambiente {@code MAIN_CLASS} (configurada via Terraform).
 */
@SpringBootApplication
public class AplicacaoReceberAvaliacao {

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoReceberAvaliacao.class, args);
    }
}
