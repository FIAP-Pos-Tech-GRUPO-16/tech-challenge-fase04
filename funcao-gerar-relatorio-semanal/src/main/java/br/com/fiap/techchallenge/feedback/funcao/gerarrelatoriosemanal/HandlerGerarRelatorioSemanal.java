package br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.GerarRelatorioSemanalCasoDeUso;

import java.util.Map;
import java.util.function.Function;

/**
 * Handler de agendamento (EventBridge Scheduler) da função {@code gerar-relatorio-semanal}.
 * O payload do agendamento não carrega dados relevantes — o período do relatório é calculado
 * pelo caso de uso a partir do relógio do sistema.
 */
class HandlerGerarRelatorioSemanal implements Function<Map<String, Object>, String> {

    private final GerarRelatorioSemanalCasoDeUso casoDeUso;

    HandlerGerarRelatorioSemanal(GerarRelatorioSemanalCasoDeUso casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @Override
    public String apply(Map<String, Object> evento) {
        casoDeUso.executar();
        return "Relatório semanal gerado e enviado";
    }
}
