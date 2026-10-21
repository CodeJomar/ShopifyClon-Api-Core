package shopify.api.core.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** A-002: la IP del visitante solo sale de X-Forwarded-For con el secreto del proxy y una IP válida. */
class IpClienteResolverTest {

    private static final String SECRETO = "secreto-de-prueba-largo";
    private static final String CONEXION = "10.0.0.1";

    @Test
    void conSecretoCorrectoUsaLaIpDeXForwardedFor() {
        IpClienteResolver resolver = new IpClienteResolver(SECRETO);

        assertThat(resolver.ipDe(peticion(SECRETO, "203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void conSecretoIncorrectoIgnoraXForwardedFor() {
        IpClienteResolver resolver = new IpClienteResolver(SECRETO);

        assertThat(resolver.ipDe(peticion("otro-secreto", "203.0.113.7"))).isEqualTo(CONEXION);
    }

    @Test
    void sinCabeceraDeSecretoIgnoraXForwardedFor() {
        IpClienteResolver resolver = new IpClienteResolver(SECRETO);

        assertThat(resolver.ipDe(peticion(null, "203.0.113.7"))).isEqualTo(CONEXION);
    }

    @Test
    void conSecretoVacioIgnoraXForwardedForAunqueLaCabeceraCoincida() {
        IpClienteResolver resolver = new IpClienteResolver("");

        assertThat(resolver.ipDe(peticion("", "203.0.113.7"))).isEqualTo(CONEXION);
        assertThat(resolver.ipDe(peticion(SECRETO, "203.0.113.7"))).isEqualTo(CONEXION);
    }

    @Test
    void conSecretoNoConfiguradoIgnoraXForwardedFor() {
        IpClienteResolver resolver = new IpClienteResolver(null);

        assertThat(resolver.ipDe(peticion(SECRETO, "203.0.113.7"))).isEqualTo(CONEXION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no-es-ip", "example.com", "999.1.1.1", "1.2.3", "203.0.113.7, 198.51.100.2",
            "", "2001:db8::1::2", "2001:db8:::1", "12345::1", ":2001:db8::1", "2001:db8:0:0:0:0:0:0:1"})
    void unaCabeceraQueNoEsUnaIpNoSeUsa(String valor) {
        IpClienteResolver resolver = new IpClienteResolver(SECRETO);

        assertThat(resolver.ipDe(peticion(SECRETO, valor))).isEqualTo(CONEXION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.7", "2001:db8::1", "::1", "fe80::1:2:3:4"})
    void unaIpV4OV6ValidaSeUsa(String valor) {
        IpClienteResolver resolver = new IpClienteResolver(SECRETO);

        assertThat(resolver.ipDe(peticion(SECRETO, valor))).isEqualTo(valor);
    }

    private static MockHttpServletRequest peticion(String secreto, String ipReenviada) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CONEXION);
        if (secreto != null) {
            request.addHeader("X-Proxy-Secreto", secreto);
        }
        if (ipReenviada != null) {
            request.addHeader("X-Forwarded-For", ipReenviada);
        }
        return request;
    }
}
