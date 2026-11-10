package br.com.fiap.techchallenge.feedback.infraestrutura.ses;

import br.com.fiap.techchallenge.feedback.aplicacao.relatorio.RelatorioSemanal;
import br.com.fiap.techchallenge.feedback.dominio.Avaliacao;
import br.com.fiap.techchallenge.feedback.dominio.NivelUrgencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnviadorEmailSesTest {

    private static final String REMETENTE = "no-reply@tech-challenge-fase04.fiap.com.br";
    private static final String EMAIL_ADMIN = "admin-teste@fiap.com.br";

    @Mock
    private SesV2Client clienteSes;

    private EnviadorEmailSes enviadorEmail;

    @BeforeEach
    void configurar() {
        FornecedorEmailAdministrador fornecedorFixo = () -> EMAIL_ADMIN;
        enviadorEmail = new EnviadorEmailSes(clienteSes, fornecedorFixo, REMETENTE);
    }

    @Test
    void deveMontarAlertaDeUrgenciaComDescricaoUrgenciaEData() {
        Avaliacao avaliacaoCritica = Avaliacao.receber("Aula com problema grave de áudio", 1, Instant.parse("2026-01-10T10:00:00Z"));

        enviadorEmail.enviarAlertaUrgencia(avaliacaoCritica);

        SendEmailRequest requisicao = capturarRequisicaoEnviada();
        assertThat(requisicao.fromEmailAddress()).isEqualTo(REMETENTE);
        assertThat(requisicao.destination().toAddresses()).containsExactly(EMAIL_ADMIN);
        assertThat(requisicao.content().simple().subject().data()).contains("urgência");

        String corpo = requisicao.content().simple().body().text().data();
        assertThat(corpo)
            .contains("Aula com problema grave de áudio")
            .contains("ALTA")
            .contains("2026-01-10T10:00:00Z");
    }

    @Test
    void deveMontarRelatorioSemanalComMediaEContagens() {
        RelatorioSemanal relatorio = new RelatorioSemanal(
            List.of(Avaliacao.receber("Aula boa", 8, Instant.parse("2026-01-05T08:00:00Z"))),
            7.5,
            Map.of(LocalDate.of(2026, 1, 5), 1L),
            Map.of(NivelUrgencia.BAIXA, 1L)
        );

        enviadorEmail.enviarRelatorioSemanal(relatorio);

        SendEmailRequest requisicao = capturarRequisicaoEnviada();
        assertThat(requisicao.destination().toAddresses()).containsExactly(EMAIL_ADMIN);

        String corpo = requisicao.content().simple().body().text().data();
        assertThat(corpo)
            .contains("7,50").doesNotContain("null")
            .contains("Aula boa");
    }

    private SendEmailRequest capturarRequisicaoEnviada() {
        ArgumentCaptor<SendEmailRequest> captura = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(clienteSes).sendEmail(captura.capture());
        return captura.getValue();
    }
}
