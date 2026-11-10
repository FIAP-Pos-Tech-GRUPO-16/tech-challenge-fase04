package br.com.fiap.techchallenge.feedback.infraestrutura.snapstart;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.warmup.SdkWarmUp;

/**
 * Gancho do AWS Lambda SnapStart (via CRaC). Sem ele, uma função restaurada de um snapshot
 * retoma com as credenciais da execution role e as conexões HTTP obtidas no momento do
 * snapshot — potencialmente obsoletas se o snapshot ficou "congelado" por um tempo antes de
 * ser restaurado.
 *
 * <p>Na restauração ({@link #afterRestore}), força a renovação das credenciais através do
 * {@link AwsCredentialsProvider} compartilhado por todos os clientes AWS SDK da função — e não
 * um provider isolado por cliente, porque só assim a renovação beneficia todos eles.
 *
 * <p>O {@code Context} do CRaC guarda apenas uma {@code WeakReference} ao recurso registrado —
 * por isso o composition root de cada função mantém uma instância desta classe como
 * {@code @Bean} singleton (referência forte, viva durante todo o ciclo de vida da aplicação).
 */
public final class GanchoPosRestauracaoSnapStart implements Resource {

    private final AwsCredentialsProvider provedorCredenciaisCompartilhado;

    public GanchoPosRestauracaoSnapStart(AwsCredentialsProvider provedorCredenciaisCompartilhado, Class<? extends SdkClient>... clientesParaAquecer) {
        this.provedorCredenciaisCompartilhado = provedorCredenciaisCompartilhado;
        SdkWarmUp.warmUp(clientesParaAquecer);
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> contexto) {
        // Nada a liberar explicitamente: os clientes usam o HTTP client baseado em
        // UrlConnection, que não mantém event loop/file descriptors próprios como o
        // cliente baseado em CRT (esse exigiria fechar recursos aqui).
    }

    @Override
    public void afterRestore(Context<? extends Resource> contexto) {
        provedorCredenciaisCompartilhado.resolveCredentials();
    }
}
