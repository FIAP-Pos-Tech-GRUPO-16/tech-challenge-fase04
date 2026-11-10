package br.com.fiap.techchallenge.feedback.aplicacao.casodeuso;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.EnviadorEmail;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificarUrgenciaCasoDeUsoTest {

    @Mock
    private EnviadorEmail enviadorEmail;

    private NotificarUrgenciaCasoDeUso casoDeUso;

    @BeforeEach
    void configurar() {
        casoDeUso = new NotificarUrgenciaCasoDeUso(enviadorEmail);
    }

    @Test
    void deveEnviarAlertaQuandoAvaliacaoForCritica() {
        Avaliacao avaliacaoCritica = Avaliacao.receber("Problema grave na aula", 1, Instant.now());

        casoDeUso.executar(avaliacaoCritica);

        verify(enviadorEmail).enviarAlertaUrgencia(avaliacaoCritica);
    }

    @Test
    void naoDeveEnviarAlertaQuandoAvaliacaoNaoForCritica() {
        Avaliacao avaliacaoNaoCritica = Avaliacao.receber("Aula tranquila", 8, Instant.now());

        casoDeUso.executar(avaliacaoNaoCritica);

        verify(enviadorEmail, never()).enviarAlertaUrgencia(avaliacaoNaoCritica);
    }
}
