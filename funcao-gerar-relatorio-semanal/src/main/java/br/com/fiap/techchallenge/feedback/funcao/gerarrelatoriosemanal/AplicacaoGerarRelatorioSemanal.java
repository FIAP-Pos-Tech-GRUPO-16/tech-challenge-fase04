package br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada Spring Boot. A classe efetivamente invocada pelo Lambda é
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker}; esta classe é
 * referenciada pela variável de ambiente {@code MAIN_CLASS} (configurada via Terraform).
 */
@SpringBootApplication
public class AplicacaoGerarRelatorioSemanal {

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoGerarRelatorioSemanal.class, args);
    }
}
