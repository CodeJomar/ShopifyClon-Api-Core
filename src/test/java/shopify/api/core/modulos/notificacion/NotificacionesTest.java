package shopify.api.core.modulos.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.envio.evento.EnvioDespachadoEvento;
import shopify.api.core.modulos.envio.evento.EnvioEntregadoEvento;
import shopify.api.core.modulos.orden.eventos.OrdenCreadaEvento;
import shopify.api.core.modulos.orden.eventos.OrdenPagadaEvento;
import shopify.api.core.modulos.pago.eventos.OrdenCanceladaEvento;
import shopify.api.core.modulos.suscripcion.evento.SuscripcionRenovadaEvento;
import shopify.api.core.modulos.suscripcion.evento.SuscripcionVencidaEvento;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CodigoRecuperacionEmitidoEvento;
import shopify.api.core.soporte.PruebaIntegracionBase;

/**
 * Notificaciones por correo (T-023). Los eventos se publican dentro de una transacción que confirma, para que los
 * escuchadores salten al commit. El envío es síncrono en pruebas ({@code app.async.sincrono=true}).
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificacionesTest extends PruebaIntegracionBase {

    @Autowired
    private ApplicationEventPublisher publicador;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID tienda;
    private String subdominio;
    private String correoContacto;

    @BeforeEach
    void preparar() {
        reset(correo);
        tienda = crearTienda(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        correoContacto = jdbc.queryForObject("SELECT \"CorreoContacto\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    // ---- usuario ----

    @Test
    void elCodigoDeActivacionLlegaAlUsuarioConSuVigencia() {
        publicarEnTransaccion(new CodigoActivacionEmitidoEvento("nuevo@prueba.test", "Ana", "482913"));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly("nuevo@prueba.test");
        assertThat(correo.getText()).contains("482913").contains("24 horas");
    }

    @Test
    void elCodigoDeRecuperacionLlegaAlUsuarioConSuVigencia() {
        publicarEnTransaccion(new CodigoRecuperacionEmitidoEvento("olvido@prueba.test", "Luis", "551204"));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly("olvido@prueba.test");
        assertThat(correo.getText()).contains("551204").contains("30 minutos");
    }

    // ---- orden ----

    @Test
    void unaOrdenCreadaAvisaAlCompradorConEnlaceYALaTienda() {
        UUID orden = ordenDePrueba("#1001", "ab12cd34token");

        publicarEnTransaccion(new OrdenCreadaEvento(tienda, orden, "#1001"));

        List<SimpleMailMessage> correos = correos(2);
        SimpleMailMessage alComprador = correos.stream().filter(m -> m.getTo()[0].equals("comprador@prueba.test")).findFirst().orElseThrow();
        assertThat(alComprador.getText()).contains("#1001").contains("/" + subdominio + "/ordenes/ab12cd34token");
        SimpleMailMessage alaTienda = correos.stream().filter(m -> m.getTo()[0].equals(correoContacto)).findFirst().orElseThrow();
        assertThat(alaTienda.getSubject()).contains("Nueva orden #1001");
    }

    @Test
    void unaOrdenPagadaAvisaAlComprador() {
        UUID orden = ordenDePrueba("#1002", "tok-pagada");

        publicarEnTransaccion(new OrdenPagadaEvento(tienda, orden));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly("comprador@prueba.test");
        assertThat(correo.getSubject()).contains("Pago confirmado").contains("#1002");
    }

    @Test
    void unaOrdenCanceladaAvisaAlCompradorConSuMotivo() {
        UUID orden = ordenDePrueba("#1003", "tok-cancelada");

        publicarEnTransaccion(new OrdenCanceladaEvento(tienda, orden, "CLIENTE"));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly("comprador@prueba.test");
        assertThat(correo.getText()).contains("tu solicitud");
    }

    // ---- envío ----

    @Test
    void unEnvioDespachadoAvisaAlCompradorConGuiaYSeguimiento() {
        UUID orden = ordenDePrueba("#1004", "tok-despachada");
        UUID envio = envio(orden, "DHL", "GUIA-998", "https://seguimiento.test/998");

        publicarEnTransaccion(new EnvioDespachadoEvento(tienda, envio, orden));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly("comprador@prueba.test");
        assertThat(correo.getText()).contains("DHL").contains("GUIA-998").contains("https://seguimiento.test/998");
    }

    @Test
    void unEnvioEntregadoAvisaAlComprador() {
        UUID orden = ordenDePrueba("#1005", "tok-entregada");
        UUID envio = envio(orden, "DHL", "GUIA-1", null);

        publicarEnTransaccion(new EnvioEntregadoEvento(tienda, envio, orden));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly("comprador@prueba.test");
        assertThat(correo.getSubject()).contains("fue entregada").contains("#1005");
    }

    // ---- suscripción ----

    @Test
    void unaSuscripcionVencidaAvisaAlPropietario() {
        publicarEnTransaccion(new SuscripcionVencidaEvento(tienda));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly(correoDelPropietario());
        assertThat(correo.getSubject()).contains("venció");
    }

    @Test
    void unaSuscripcionRenovadaAvisaAlPropietario() {
        publicarEnTransaccion(new SuscripcionRenovadaEvento(tienda));

        SimpleMailMessage correo = unCorreo();
        assertThat(correo.getTo()).containsExactly(correoDelPropietario());
        assertThat(correo.getSubject()).contains("se renovó");
    }

    // ---- criterios transversales ----

    @Test
    void unEventoDeUnaTransaccionQueHaceRollbackNoEnviaCorreo() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                transaccion.executeWithoutResult(estado -> {
                    publicador.publishEvent(new CodigoActivacionEmitidoEvento("nuevo@prueba.test", "Ana", "111222"));
                    throw new IllegalStateException("la operación falla después de publicar");
                }));

        verify(correo, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void unFalloDelCorreoNoRompeLaOperacionQueLoPublicoYSeRegistraUnaAdvertencia(CapturedOutput salida) {
        doThrow(new MailSendException("servidor de correo caído")).when(correo).send(any(SimpleMailMessage.class));

        assertThatCode(() -> transaccion.executeWithoutResult(
                estado -> publicador.publishEvent(new CodigoActivacionEmitidoEvento("nuevo@prueba.test", "Ana", "333444"))))
                .doesNotThrowAnyException();
        verify(correo, times(1)).send(any(SimpleMailMessage.class));
        assertThat(salida.getAll()).contains("No se pudo enviar el correo de activación");
    }

    @Test
    void elCodigoNuncaApareceEnLosLogs(CapturedOutput salida) {
        publicarEnTransaccion(new CodigoActivacionEmitidoEvento("nuevo@prueba.test", "Ana", "909090"));
        publicarEnTransaccion(new CodigoRecuperacionEmitidoEvento("olvido@prueba.test", "Luis", "808080"));

        assertThat(salida.getAll()).doesNotContain("909090").doesNotContain("808080");
    }

    // ---- ayudas ----

    private void publicarEnTransaccion(Object evento) {
        transaccion.executeWithoutResult(estado -> publicador.publishEvent(evento));
    }

    private SimpleMailMessage unCorreo() {
        return correos(1).get(0);
    }

    private List<SimpleMailMessage> correos(int esperados) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo, times(esperados)).send(captor.capture());
        return captor.getAllValues();
    }

    private String correoDelPropietario() {
        return jdbc.queryForObject("SELECT u.\"CorreoElectronico\" FROM \"Usuarios\" u JOIN \"Tiendas\" t "
                + "ON t.\"PropietarioId\" = u.\"Id\" WHERE t.\"Id\" = ?", String.class, tienda);
    }

    /** Orden mínima con comprador conocido y token; el resto de columnas toman sus valores por defecto. */
    private UUID ordenDePrueba(String numero, String token) {
        return jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", \"NombreCliente\", "
                + "\"Subtotal\", \"Total\", \"TokenAcceso\") VALUES (?, ?, 'comprador@prueba.test', 'Comprador', 25.00, 25.00, ?) "
                + "RETURNING \"Id\"", UUID.class, tienda, numero, token);
    }

    private UUID envio(UUID orden, String transportista, String guia, String url) {
        return jdbc.queryForObject("INSERT INTO \"Envios\" (\"OrdenId\", \"EmpresaTransporte\", \"NumeroGuia\", "
                + "\"UrlSeguimiento\", \"Estado\") VALUES (?, ?, ?, ?, 'EN_TRANSITO') RETURNING \"Id\"", UUID.class,
                orden, transportista, guia, url);
    }
}
