package br.com.fiap.techchallenge.feedback.funcao.gerarrelatoriosemanal;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.GerarRelatorioSemanalCasoDeUso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HandlerGerarRelatorioSemanalTest {

    @Mock
    private GerarRelatorioSemanalCasoDeUso casoDeUso;

    @Test
    void deveAcionarOCasoDeUsoAoSerInvocadoPeloAgendamento() {
        HandlerGerarRelatorioSemanal handler = new HandlerGerarRelatorioSemanal(casoDeUso);

        String resultado = handler.apply(Map.of());

        verify(casoDeUso).executar();
        assertThat(resultado).isNotBlank();
    }
}
