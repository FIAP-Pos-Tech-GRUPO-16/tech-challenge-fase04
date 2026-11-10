package br.com.fiap.techchallenge.feedback.aplicacao.casodeuso;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.aplicacao.relatorio.RelatorioSemanal;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrega as avaliações dos últimos 7 dias e envia o relatório semanal por e-mail,
 * disparado por agendamento (EventBridge Scheduler).
 */
public class GerarRelatorioSemanalCasoDeUso {

    private static final int DIAS_DO_RELATORIO = 7;

    private final RepositorioAvaliacao repositorioAvaliacao;
    private final EnviadorEmail enviadorEmail;
    private final Clock relogio;

    public GerarRelatorioSemanalCasoDeUso(RepositorioAvaliacao repositorioAvaliacao, EnviadorEmail enviadorEmail, Clock relogio) {
        this.repositorioAvaliacao = repositorioAvaliacao;
        this.enviadorEmail = enviadorEmail;
        this.relogio = relogio;
    }

    public void executar() {
        Instant fim = Instant.now(relogio);
        Instant inicio = fim.minus(DIAS_DO_RELATORIO, ChronoUnit.DAYS);

        List<Avaliacao> avaliacoes = repositorioAvaliacao.buscarPorPeriodo(inicio, fim);

        RelatorioSemanal relatorio = new RelatorioSemanal(
            avaliacoes,
            calcularMediaNotas(avaliacoes),
            agruparPorDia(avaliacoes),
            agruparPorUrgencia(avaliacoes)
        );

        enviadorEmail.enviarRelatorioSemanal(relatorio);
    }

    private double calcularMediaNotas(List<Avaliacao> avaliacoes) {
        return avaliacoes.stream()
            .mapToInt(Avaliacao::nota)
            .average()
            .orElse(0.0);
    }

    private Map<LocalDate, Long> agruparPorDia(List<Avaliacao> avaliacoes) {
        return avaliacoes.stream()
            .collect(Collectors.groupingBy(
                avaliacao -> LocalDate.ofInstant(avaliacao.dataEnvio(), ZoneOffset.UTC),
                Collectors.counting()
            ));
    }

    private Map<NivelUrgencia, Long> agruparPorUrgencia(List<Avaliacao> avaliacoes) {
        return avaliacoes.stream()
            .collect(Collectors.groupingBy(Avaliacao::nivelUrgencia, Collectors.counting()));
    }
}
