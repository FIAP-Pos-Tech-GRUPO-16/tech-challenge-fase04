package br.com.fiap.techchallenge.feedback.infraestrutura.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Representação da avaliação na tabela DynamoDB. Chave primária: {@code id}.
 * Índice secundário {@value #NOME_INDICE_POR_DATA} (partição constante {@code tipo},
 * ordenação por {@code dataEnvio}) permite consultar por período sem Scan na tabela toda.
 */
@DynamoDbBean
public class AvaliacaoItemDynamoDb {

    public static final String NOME_INDICE_POR_DATA = "gsi_data_envio";
    public static final String VALOR_TIPO_AVALIACAO = "AVALIACAO";

    private String id;
    private String descricao;
    private int nota;
    private String nivelUrgencia;
    private String dataEnvio;
    private String tipo;

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getNota() {
        return nota;
    }

    public void setNota(int nota) {
        this.nota = nota;
    }

    public String getNivelUrgencia() {
        return nivelUrgencia;
    }

    public void setNivelUrgencia(String nivelUrgencia) {
        this.nivelUrgencia = nivelUrgencia;
    }

    @DynamoDbSecondarySortKey(indexNames = NOME_INDICE_POR_DATA)
    public String getDataEnvio() {
        return dataEnvio;
    }

    public void setDataEnvio(String dataEnvio) {
        this.dataEnvio = dataEnvio;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = NOME_INDICE_POR_DATA)
    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
