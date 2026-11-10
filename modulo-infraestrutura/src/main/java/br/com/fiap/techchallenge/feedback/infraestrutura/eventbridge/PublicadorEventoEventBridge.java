package br.com.fiap.techchallenge.feedback.infraestrutura.eventbridge;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.PublicadorEvento;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

/**
 * Implementação da porta {@link PublicadorEvento} usando o Amazon EventBridge.
 * Publica o evento {@code AvaliacaoRecebida} no barramento configurado; o roteamento
 * até os consumidores (ex.: notificação de urgência) é definido por regras na infraestrutura.
 */
public class PublicadorEventoEventBridge implements PublicadorEvento {

    static final String DETAIL_TYPE_AVALIACAO_RECEBIDA = "AvaliacaoRecebida";

    private final EventBridgeClient clienteEventBridge;
    private final String nomeBarramento;
    private final String origem;
    private final ObjectMapper mapeadorJson;

    public PublicadorEventoEventBridge(EventBridgeClient clienteEventBridge, String nomeBarramento, String origem, ObjectMapper mapeadorJson) {
        this.clienteEventBridge = clienteEventBridge;
        this.nomeBarramento = nomeBarramento;
        this.origem = origem;
        this.mapeadorJson = mapeadorJson;
    }

    @Override
    public void publicarAvaliacaoRecebida(Avaliacao avaliacao) {
        PutEventsRequestEntry entrada = PutEventsRequestEntry.builder()
            .eventBusName(nomeBarramento)
            .source(origem)
            .detailType(DETAIL_TYPE_AVALIACAO_RECEBIDA)
            .detail(serializar(avaliacao))
            .build();

        PutEventsResponse resposta = clienteEventBridge.putEvents(requisicao -> requisicao.entries(entrada));

        if (resposta.failedEntryCount() > 0) {
            throw new PublicacaoEventoFalhouException("Falha ao publicar evento AvaliacaoRecebida no EventBridge: " + resposta.entries());
        }
    }

    private String serializar(Avaliacao avaliacao) {
        AvaliacaoEventoPayload payload = new AvaliacaoEventoPayload(
            avaliacao.id().toString(),
            avaliacao.descricao(),
            avaliacao.nota(),
            avaliacao.nivelUrgencia().name(),
            avaliacao.dataEnvio().toString()
        );

        try {
            return mapeadorJson.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PublicacaoEventoFalhouException("Falha ao serializar evento AvaliacaoRecebida", e);
        }
    }
}
