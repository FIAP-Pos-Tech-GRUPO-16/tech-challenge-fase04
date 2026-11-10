package br.com.fiap.techchallenge.feedback.aplicacao.casodeuso;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.aplicacao.relatorio.RelatorioSemanal;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarRelatorioSemanalCasoDeUsoTest {

    @Mock
    private RepositorioAvaliacao repositorioAvaliacao;

    @Mock
    private EnviadorEmail enviadorEmail;

    private GerarRelatorioSemanalCasoDeUso casoDeUso;

    @BeforeEach
    void configurar() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC);
        casoDeUso = new GerarRelatorioSemanalCasoDeUso(repositorioAvaliacao, enviadorEmail, relogioFixo);
    }

    @Test
    void deveAgregarAvaliacoesDaSemanaECalcularMediaEContagens() {
        List<Avaliacao> avaliacoesDaSemana = List.of(
            new Avaliacao(UUID.randomUUID(), "Aula ótima", 9, NivelUrgencia.BAIXA, Instant.parse("2026-01-05T08:00:00Z")),
            new Avaliacao(UUID.randomUUID(), "Aula ok", 5, NivelUrgencia.MEDIA, Instant.parse("2026-01-05T09:00:00Z")),
            new Avaliacao(UUID.randomUUID(), "Aula ruim", 1, NivelUrgencia.ALTA, Instant.parse("2026-01-06T10:00:00Z"))
        );
        when(repositorioAvaliacao.buscarPorPeriodo(any(), any())).thenReturn(avaliacoesDaSemana);

        casoDeUso.executar();

        ArgumentCaptor<RelatorioSemanal> captura = ArgumentCaptor.forClass(RelatorioSemanal.class);
        verify(enviadorEmail).enviarRelatorioSemanal(captura.capture());

        RelatorioSemanal relatorio = captura.getValue();
        assertThat(relatorio.avaliacoes()).containsExactlyElementsOf(avaliacoesDaSemana);
        assertThat(relatorio.mediaNotas()).isEqualTo(5.0);
        assertThat(relatorio.quantidadePorDia())
            .containsEntry(LocalDate.of(2026, 1, 5), 2L)
            .containsEntry(LocalDate.of(2026, 1, 6), 1L);
        assertThat(relatorio.quantidadePorUrgencia())
            .containsEntry(NivelUrgencia.BAIXA, 1L)
            .containsEntry(NivelUrgencia.MEDIA, 1L)
            .containsEntry(NivelUrgencia.ALTA, 1L);
    }

    @Test
    void deveConsultarOsUltimosSeteDiasAPartirDoRelogioInjetado() {
        when(repositorioAvaliacao.buscarPorPeriodo(any(), any())).thenReturn(List.of());

        casoDeUso.executar();

        verify(repositorioAvaliacao).buscarPorPeriodo(
            Instant.parse("2026-01-03T10:00:00Z"),
            Instant.parse("2026-01-10T10:00:00Z")
        );
    }

    @Test
    void deveEnviarMediaZeroQuandoNaoHouverAvaliacoesNaSemana() {
        when(repositorioAvaliacao.buscarPorPeriodo(any(), any())).thenReturn(List.of());

        casoDeUso.executar();

        ArgumentCaptor<RelatorioSemanal> captura = ArgumentCaptor.forClass(RelatorioSemanal.class);
        verify(enviadorEmail).enviarRelatorioSemanal(captura.capture());
        assertThat(captura.getValue().mediaNotas()).isZero();
    }
}
