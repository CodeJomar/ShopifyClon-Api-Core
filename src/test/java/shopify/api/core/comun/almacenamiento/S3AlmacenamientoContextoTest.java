package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * El proveedor S3 solo se crea con {@code app.almacenamiento.proveedor=s3}; las demás pruebas usan Supabase y nunca lo
 * instancian, así que esta es la única que comprueba que Spring puede construirlo (un constructor ambiguo tumbó el
 * arranque en producción).
 */
class S3AlmacenamientoContextoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(S3Almacenamiento.class, ArchivoPublicoControlador.class);

    @Test
    void conProveedorS3SeCreaConLasPropiedadesDeRailway() {
        contexto.withPropertyValues("app.almacenamiento.proveedor=s3",
                        "app.s3.endpoint=https://s3.ejemplo.test", "app.s3.region=auto", "app.s3.bucket=imagenes",
                        "app.s3.access-key=clave", "app.s3.secret-key=secreto", "app.s3.url-publica=https://api.ejemplo.test/api/v1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(S3Almacenamiento.class);
                    assertThat(ctx).hasSingleBean(ArchivoPublicoControlador.class);
                });
    }

    @Test
    void sinCredencialesArrancaIgualYAvisaEnLugarDeTumbarLaApi() {
        contexto.withPropertyValues("app.almacenamiento.proveedor=s3").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(S3Almacenamiento.class).leer("a/b.png")).isEmpty();
        });
    }
}
