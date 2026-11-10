package br.com.fiap.techchallenge.feedback.aplicacao.relatorio;

import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Consolidado semanal de avaliações: a lista de avaliações do período (cada uma já
 * carregando descrição, urgência e data de envio), a média das notas e as contagens
 * exigidas pelo relatório (por dia e por nível de urgência).
 */
public record RelatorioSemanal(
    List<Avaliacao> avaliacoes,
    double mediaNotas,
    Map<LocalDate, Long> quantidadePorDia,
    Map<NivelUrgencia, Long> quantidadePorUrgencia
) {
}
