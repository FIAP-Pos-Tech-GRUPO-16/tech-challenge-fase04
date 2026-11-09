package br.com.fiap.techchallenge.feedback.dominio;

/**
 * Classifica a urgência de uma avaliação a partir da nota (0 a 10):
 * 0-3 = ALTA, 4-6 = MEDIA, 7-10 = BAIXA.
 */
public final class ClassificadorUrgencia {

    private static final int LIMITE_SUPERIOR_ALTA = 3;
    private static final int LIMITE_SUPERIOR_MEDIA = 6;

    private ClassificadorUrgencia() {
    }

    public static NivelUrgencia classificar(int nota) {
        if (nota <= LIMITE_SUPERIOR_ALTA) {
            return NivelUrgencia.ALTA;
        }
        if (nota <= LIMITE_SUPERIOR_MEDIA) {
            return NivelUrgencia.MEDIA;
        }
        return NivelUrgencia.BAIXA;
    }
}
