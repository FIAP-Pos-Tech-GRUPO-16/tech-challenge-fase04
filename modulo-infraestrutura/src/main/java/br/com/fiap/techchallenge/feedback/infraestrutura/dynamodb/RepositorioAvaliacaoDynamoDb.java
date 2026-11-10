package br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb;

import br.com.fiap.techchallenge.feedback.aplicacao.porta.RepositorioAvaliacao;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;

/**
 * Implementação da porta {@link RepositorioAvaliacao} usando o DynamoDB Enhanced Client.
 * A consulta por período usa o índice secundário {@link AvaliacaoItemDynamoDb#NOME_INDICE_POR_DATA}
 * em vez de um Scan completo da tabela.
 */
public class RepositorioAvaliacaoDynamoDb implements RepositorioAvaliacao {

    private final DynamoDbTable<AvaliacaoItemDynamoDb> tabela;

    public RepositorioAvaliacaoDynamoDb(DynamoDbEnhancedClient clienteIntensificado, String nomeTabela) {
        this.tabela = clienteIntensificado.table(nomeTabela, TableSchema.fromBean(AvaliacaoItemDynamoDb.class));
    }

    @Override
    public void salvar(Avaliacao avaliacao) {
        tabela.putItem(AvaliacaoItemMapper.paraItem(avaliacao));
    }

    @Override
    public List<Avaliacao> buscarPorPeriodo(Instant inicio, Instant fim) {
        DynamoDbIndex<AvaliacaoItemDynamoDb> indice = tabela.index(AvaliacaoItemDynamoDb.NOME_INDICE_POR_DATA);

        QueryConditional condicaoPorPeriodo = QueryConditional.sortBetween(
            Key.builder().partitionValue(AvaliacaoItemDynamoDb.VALOR_TIPO_AVALIACAO).sortValue(inicio.toString()).build(),
            Key.builder().partitionValue(AvaliacaoItemDynamoDb.VALOR_TIPO_AVALIACAO).sortValue(fim.toString()).build()
        );

        return indice.query(requisicao -> requisicao.queryConditional(condicaoPorPeriodo)).stream()
            .flatMap(pagina -> pagina.items().stream())
            .map(AvaliacaoItemMapper::paraDominio)
            .toList();
    }
}
