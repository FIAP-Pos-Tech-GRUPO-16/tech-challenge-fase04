package br.com.fiap.techchallenge.feedback.dominio;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificadorUrgenciaTest {

    @ParameterizedTest
    @CsvSource({
        "0, ALTA",
        "3, ALTA",
        "4, MEDIA",
        "6, MEDIA",
        "7, BAIXA",
        "10, BAIXA"
    })
    void deveClassificarUrgenciaConformeFaixaDaNota(int nota, NivelUrgencia nivelEsperado) {
        assertThat(ClassificadorUrgencia.classificar(nota)).isEqualTo(nivelEsperado);
    }
}
