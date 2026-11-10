package br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb;

import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;

import java.time.Instant;
import java.util.UUID;

/**
 * Converte entre a entidade de domínio {@link Avaliacao} e o item persistido no DynamoDB.
 */
final class AvaliacaoItemMapper {

    private AvaliacaoItemMapper() {
    }

    static AvaliacaoItemDynamoDb paraItem(Avaliacao avaliacao) {
        AvaliacaoItemDynamoDb item = new AvaliacaoItemDynamoDb();
        item.setId(avaliacao.id().toString());
        item.setDescricao(avaliacao.descricao());
        item.setNota(avaliacao.nota());
        item.setNivelUrgencia(avaliacao.nivelUrgencia().name());
        item.setDataEnvio(avaliacao.dataEnvio().toString());
        item.setTipo(AvaliacaoItemDynamoDb.VALOR_TIPO_AVALIACAO);
        return item;
    }

    static Avaliacao paraDominio(AvaliacaoItemDynamoDb item) {
        return new Avaliacao(
            UUID.fromString(item.getId()),
            item.getDescricao(),
            item.getNota(),
            NivelUrgencia.valueOf(item.getNivelUrgencia()),
            Instant.parse(item.getDataEnvio())
        );
    }
}
