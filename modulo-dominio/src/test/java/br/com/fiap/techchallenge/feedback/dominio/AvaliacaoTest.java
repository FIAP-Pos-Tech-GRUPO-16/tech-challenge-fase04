package br.com.fiap.techchallenge.feedback.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvaliacaoTest {

    @Test
    void deveReceberAvaliacaoValidaClassificandoUrgenciaAutomaticamente() {
        Avaliacao avaliacao = Avaliacao.receber("Aula muito boa", 9, Instant.now());

        assertThat(avaliacao.id()).isNotNull();
        assertThat(avaliacao.descricao()).isEqualTo("Aula muito boa");
        assertThat(avaliacao.nivelUrgencia()).isEqualTo(NivelUrgencia.BAIXA);
        assertThat(avaliacao.isCritica()).isFalse();
    }

    @Test
    void deveMarcarComoCriticaQuandoUrgenciaForAlta() {
        Avaliacao avaliacao = Avaliacao.receber("Aula com problemas graves", 2, Instant.now());

        assertThat(avaliacao.nivelUrgencia()).isEqualTo(NivelUrgencia.ALTA);
        assertThat(avaliacao.isCritica()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11, 100})
    void deveRejeitarNotaForaDoIntervaloPermitido(int notaInvalida) {
        assertThatThrownBy(() -> Avaliacao.receber("Descrição válida", notaInvalida, Instant.now()))
            .isInstanceOf(AvaliacaoInvalidaException.class);
    }

    @Test
    void deveRejeitarDescricaoEmBranco() {
        assertThatThrownBy(() -> Avaliacao.receber("   ", 5, Instant.now()))
            .isInstanceOf(AvaliacaoInvalidaException.class);
    }

    @Test
    void deveRemoverEspacosDasExtremidadesDaDescricao() {
        Avaliacao avaliacao = Avaliacao.receber("  Aula ótima  ", 8, Instant.now());

        assertThat(avaliacao.descricao()).isEqualTo("Aula ótima");
    }
}
