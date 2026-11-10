package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.NotificarUrgenciaCasoDeUso;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HandlerNotificarUrgenciaTest {

    @Mock
    private NotificarUrgenciaCasoDeUso casoDeUso;

    private HandlerNotificarUrgencia handler;

    @BeforeEach
    void configurar() {
        ObjectMapper mapeadorJson = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        handler = new HandlerNotificarUrgencia(casoDeUso, mapeadorJson);
    }

    @Test
    void deveExtrairAvaliacaoDoEnvelopeEventBridgeEChamarCasoDeUso() {
        UUID id = UUID.randomUUID();
        String evento = """
            {
              "version": "0",
              "id": "evento-123",
              "detail-type": "AvaliacaoRecebida",
              "source": "tech-challenge.feedback",
              "time": "2026-01-10T10:00:00Z",
              "detail": {
                "id": "%s",
                "descricao": "Aula com problema grave",
                "nota": 1,
                "nivelUrgencia": "ALTA",
                "dataEnvio": "2026-01-10T10:00:00Z"
              }
            }
            """.formatted(id);

        handler.apply(evento);

        ArgumentCaptor<Avaliacao> captura = ArgumentCaptor.forClass(Avaliacao.class);
        verify(casoDeUso).executar(captura.capture());

        Avaliacao avaliacao = captura.getValue();
        assertThat(avaliacao.id()).isEqualTo(id);
        assertThat(avaliacao.descricao()).isEqualTo("Aula com problema grave");
        assertThat(avaliacao.nivelUrgencia()).isEqualTo(NivelUrgencia.ALTA);
        assertThat(avaliacao.dataEnvio()).isEqualTo(Instant.parse("2026-01-10T10:00:00Z"));
    }

    @Test
    void deveLancarExcecaoQuandoEventoForMalformado() {
        assertThatThrownBy(() -> handler.apply("{ isso nao é json válido"))
            .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void deveLancarExcecaoQuandoDetalheEstiverAusente() {
        assertThatThrownBy(() -> handler.apply("{}"))
            .isInstanceOf(EventoInvalidoException.class);
    }
}
