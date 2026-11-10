package br.com.fiap.techchallenge.feedback.funcao.receberavaliacao;

import br.com.fiap.techchallenge.feedback.aplicacao.casodeuso.ReceberAvaliacaoCasoDeUso;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.AvaliacaoInvalidaException;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Function;

/**
 * Handler HTTP (API Gateway) da função {@code receber-avaliacao}: único responsável por
 * traduzir a requisição/resposta HTTP. A regra de negócio fica inteira no caso de uso.
 */
class HandlerReceberAvaliacao implements Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final int STATUS_CRIADO = 201;
    private static final int STATUS_REQUISICAO_INVALIDA = 400;

    private final ReceberAvaliacaoCasoDeUso casoDeUso;
    private final ObjectMapper mapeadorJson;

    HandlerReceberAvaliacao(ReceberAvaliacaoCasoDeUso casoDeUso, ObjectMapper mapeadorJson) {
        this.casoDeUso = casoDeUso;
        this.mapeadorJson = mapeadorJson;
    }

    @Override
    public APIGatewayV2HTTPResponse apply(APIGatewayV2HTTPEvent requisicao) {
        try {
            RequisicaoAvaliacao corpo = lerCorpo(requisicao);
            Avaliacao avaliacao = casoDeUso.executar(corpo.descricao(), corpo.nota());
            return responder(STATUS_CRIADO, new RespostaAvaliacao(avaliacao.id().toString(), avaliacao.nivelUrgencia().name()));
        } catch (AvaliacaoInvalidaException | CorpoRequisicaoInvalidoException e) {
            return responder(STATUS_REQUISICAO_INVALIDA, new RespostaErro(e.getMessage()));
        }
    }

    private RequisicaoAvaliacao lerCorpo(APIGatewayV2HTTPEvent requisicao) {
        if (requisicao.getBody() == null || requisicao.getBody().isBlank()) {
            throw new CorpoRequisicaoInvalidoException("Corpo da requisição não pode ser vazio");
        }

        RequisicaoAvaliacao corpo;
        try {
            corpo = mapeadorJson.readValue(requisicao.getBody(), RequisicaoAvaliacao.class);
        } catch (JsonProcessingException e) {
            throw new CorpoRequisicaoInvalidoException("Corpo da requisição não é um JSON válido");
        }

        if (corpo.nota() == null) {
            throw new CorpoRequisicaoInvalidoException("Campo 'nota' é obrigatório");
        }

        return corpo;
    }

    private APIGatewayV2HTTPResponse responder(int status, Object corpo) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(mapeadorJson.writeValueAsString(corpo))
                .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar resposta HTTP", e);
        }
    }
}
