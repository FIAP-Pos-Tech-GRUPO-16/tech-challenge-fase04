package br.com.fiap.techchallenge.feedback.funcao.notificarurgencia;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.NotificarUrgenciaCasoDeUso;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handler de evento (EventBridge) da função {@code notificar-urgencia}: único responsável por
 * traduzir o evento recebido. A decisão de notificar ou não é do caso de uso.
 */
class HandlerNotificarUrgencia implements Function<String, String> {

    private final NotificarUrgenciaCasoDeUso casoDeUso;
    private final ObjectMapper mapeadorJson;

    HandlerNotificarUrgencia(NotificarUrgenciaCasoDeUso casoDeUso, ObjectMapper mapeadorJson) {
        this.casoDeUso = casoDeUso;
        this.mapeadorJson = mapeadorJson;
    }

    @Override
    public String apply(String eventoBruto) {
        Avaliacao avaliacao = extrairAvaliacao(eventoBruto);
        casoDeUso.executar(avaliacao);
        return "Avaliação processada: " + avaliacao.id();
    }

    private Avaliacao extrairAvaliacao(String eventoBruto) {
        try {
            DetalheAvaliacao detalhe = mapeadorJson.readValue(eventoBruto, EventoEntradaAvaliacaoRecebida.class).detail();
            return new Avaliacao(
                UUID.fromString(detalhe.id()),
                detalhe.descricao(),
                detalhe.nota(),
                NivelUrgencia.valueOf(detalhe.nivelUrgencia()),
                Instant.parse(detalhe.dataEnvio())
            );
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
            throw new EventoInvalidoException("Evento AvaliacaoRecebida malformado", e);
        }
    }
}
