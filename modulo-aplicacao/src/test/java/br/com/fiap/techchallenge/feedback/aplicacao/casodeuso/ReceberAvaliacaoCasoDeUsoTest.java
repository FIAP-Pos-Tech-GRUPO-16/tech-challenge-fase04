package br.com.fiap.techchallenge.feedback.aplicacao.casodeuso;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.PublicadorEvento;
import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceberAvaliacaoCasoDeUsoTest {

    @Mock
    private RepositorioAvaliacao repositorioAvaliacao;

    @Mock
    private PublicadorEvento publicadorEvento;

    private ReceberAvaliacaoCasoDeUso casoDeUso;

    @BeforeEach
    void configurar() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC);
        casoDeUso = new ReceberAvaliacaoCasoDeUso(repositorioAvaliacao, publicadorEvento, relogioFixo);
    }

    @Test
    void devePersistirEPublicarEventoAoReceberAvaliacaoValida() {
        Avaliacao avaliacaoRecebida = casoDeUso.executar("Aula excelente", 9);

        assertThat(avaliacaoRecebida.nivelUrgencia()).isEqualTo(NivelUrgencia.BAIXA);
        assertThat(avaliacaoRecebida.dataEnvio()).isEqualTo(Instant.parse("2026-01-10T10:00:00Z"));

        ArgumentCaptor<Avaliacao> capturaRepositorio = ArgumentCaptor.forClass(Avaliacao.class);
        verify(repositorioAvaliacao).salvar(capturaRepositorio.capture());
        assertThat(capturaRepositorio.getValue()).isEqualTo(avaliacaoRecebida);

        ArgumentCaptor<Avaliacao> capturaEvento = ArgumentCaptor.forClass(Avaliacao.class);
        verify(publicadorEvento).publicarAvaliacaoRecebida(capturaEvento.capture());
        assertThat(capturaEvento.getValue()).isEqualTo(avaliacaoRecebida);
    }
}
