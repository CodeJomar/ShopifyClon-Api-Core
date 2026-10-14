package shopify.api.core.modulos.pago.pasarela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ExcepcionNegocio;

/** El adaptador real de Stripe sin red: sin clave, firmas y eventos verificados por el SDK. */
class StripePasarelaTest {

    private static final String SECRETO_WEBHOOK = "whsec_de_prueba";

    @Test
    void sinClaveNoSeIniciaUnPagoEsPAG002() {
        StripePasarela stripe = new StripePasarela("", "");

        assertThatThrownBy(() -> stripe.crearIntento(UUID.randomUUID(), UUID.randomUUID(), 10000L, "USD"))
                .isInstanceOf(ConflictoEstadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("PAG-002"));
    }

    @Test
    void sinSecretoDeWebhookSeRechazaConPAG003() {
        StripePasarela stripe = new StripePasarela("sk_test_x", "");

        assertThatThrownBy(() -> stripe.verificarWebhook("{}", "t=1,v1=00"))
                .isInstanceOf(AccesoDenegadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("PAG-003"));
    }

    @Test
    void unaFirmaQueNoCoincideSeRechazaConPAG003() {
        StripePasarela stripe = new StripePasarela("sk_test_x", SECRETO_WEBHOOK);

        assertThatThrownBy(() -> stripe.verificarWebhook("{}", "t=1,v1=00"))
                .isInstanceOf(AccesoDenegadoException.class)
                .satisfies(ex -> assertThat(((ExcepcionNegocio) ex).getCodigo()).isEqualTo("PAG-003"));
    }

    @Test
    void unEventoConFirmaValidaSeVerificaYDevuelveElIntentoYElMonto() throws Exception {
        StripePasarela stripe = new StripePasarela("sk_test_x", SECRETO_WEBHOOK);
        String cuerpo = "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"2024-06-20\","
                + "\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_1\","
                + "\"object\":\"payment_intent\",\"amount\":10000}}}";
        String marca = String.valueOf(System.currentTimeMillis() / 1000);

        EventoPasarela evento = stripe.verificarWebhook(cuerpo, "t=" + marca + ",v1=" + firmaStripe(marca, cuerpo));

        assertThat(evento.tipo()).isEqualTo("payment_intent.succeeded");
        assertThat(evento.intentoId()).isEqualTo("pi_1");
        assertThat(evento.montoCentavos()).isEqualTo(10000L);
    }

    /** Firma según el esquema de Stripe: HMAC-SHA256 de "marca.cuerpo" con el secreto del webhook. */
    private static String firmaStripe(String marca, String cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((marca + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
    }
}
