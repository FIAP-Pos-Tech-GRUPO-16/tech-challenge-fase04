package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.ReceberAvaliacaoCasoDeUso;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.AvaliacaoInvalidaException;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlerReceberAvaliacaoTest {

    @Mock
    private ReceberAvaliacaoCasoDeUso casoDeUso;

    private HandlerReceberAvaliacao handler;

    @BeforeEach
    void configurar() {
        handler = new HandlerReceberAvaliacao(casoDeUso, new ObjectMapper());
    }

    @Test
    void deveResponderCriadoQuandoRequisicaoForValida() {
        Avaliacao avaliacao = new Avaliacao(UUID.randomUUID(), "Aula boa", 8, NivelUrgencia.BAIXA, Instant.now());
        when(casoDeUso.executar(any(), anyInt())).thenReturn(avaliacao);

        APIGatewayV2HTTPResponse resposta = handler.apply(requisicaoCom("""
            {"descricao": "Aula boa", "nota": 8}
            """));

        assertThat(resposta.getStatusCode()).isEqualTo(201);
        assertThat(resposta.getBody())
            .contains(avaliacao.id().toString())
            .contains("BAIXA");
    }

    @Test
    void deveResponderRequisicaoInvalidaQuandoCorpoForVazio() {
        APIGatewayV2HTTPResponse resposta = handler.apply(requisicaoCom(null));

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("vazio");
    }

    @Test
    void deveResponderRequisicaoInvalidaQuandoJsonForMalformado() {
        APIGatewayV2HTTPResponse resposta = handler.apply(requisicaoCom("{descricao: invalido"));

        assertThat(resposta.getStatusCode()).isEqualTo(400);
    }

    @Test
    void deveResponderRequisicaoInvalidaQuandoNotaForAusente() {
        APIGatewayV2HTTPResponse resposta = handler.apply(requisicaoCom("""
            {"descricao": "Aula boa"}
            """));

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("nota");
    }

    @Test
    void deveResponderRequisicaoInvalidaQuandoRegraDeDominioForViolada() {
        when(casoDeUso.executar(any(), anyInt())).thenThrow(new AvaliacaoInvalidaException("Nota deve estar entre 0 e 10"));

        APIGatewayV2HTTPResponse resposta = handler.apply(requisicaoCom("""
            {"descricao": "Aula boa", "nota": 99}
            """));

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("Nota deve estar entre 0 e 10");
    }

    private APIGatewayV2HTTPEvent requisicaoCom(String corpo) {
        APIGatewayV2HTTPEvent requisicao = new APIGatewayV2HTTPEvent();
        requisicao.setBody(corpo);
        return requisicao;
    }
}
