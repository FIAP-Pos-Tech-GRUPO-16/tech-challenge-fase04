package br.com.fiap.techchallenge.feedback.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Avaliação enviada por um estudante sobre uma aula: descrição, nota (0-10) e o
 * nível de urgência derivado da nota no momento do recebimento.
 */
public record Avaliacao(UUID id, String descricao, int nota, NivelUrgencia nivelUrgencia, Instant dataEnvio) {

    private static final int NOTA_MINIMA = 0;
    private static final int NOTA_MAXIMA = 10;
    private static final int DESCRICAO_TAMANHO_MAXIMO = 1000;

    public Avaliacao {
        Objects.requireNonNull(id, "Identificador da avaliação é obrigatório");
        Objects.requireNonNull(nivelUrgencia, "Nível de urgência é obrigatório");
        Objects.requireNonNull(dataEnvio, "Data de envio é obrigatória");
        validarNota(nota);
        descricao = validarDescricao(descricao);
    }

    public static Avaliacao receber(String descricao, int nota, Instant dataEnvio) {
        NivelUrgencia nivelUrgencia = ClassificadorUrgencia.classificar(nota);
        return new Avaliacao(UUID.randomUUID(), descricao, nota, nivelUrgencia, dataEnvio);
    }

    public boolean isCritica() {
        return nivelUrgencia == NivelUrgencia.ALTA;
    }

    private static String validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new AvaliacaoInvalidaException("Descrição da avaliação não pode ser vazia");
        }
        String descricaoSemEspacos = descricao.trim();
        if (descricaoSemEspacos.length() > DESCRICAO_TAMANHO_MAXIMO) {
            throw new AvaliacaoInvalidaException(
                "Descrição da avaliação não pode ter mais de %d caracteres".formatted(DESCRICAO_TAMANHO_MAXIMO));
        }
        return descricaoSemEspacos;
    }

    private static void validarNota(int nota) {
        if (nota < NOTA_MINIMA || nota > NOTA_MAXIMA) {
            throw new AvaliacaoInvalidaException("Nota deve estar entre %d e %d".formatted(NOTA_MINIMA, NOTA_MAXIMA));
        }
    }
}
