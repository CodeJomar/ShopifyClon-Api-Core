package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;

class SuscripcionTest extends PruebaSuscripcionBase {

    @Autowired
    private SuscripcionServicio suscripciones;

    private UUID nuevaTienda() {
        return crearTienda(crearUsuarioActivo(false));
    }

    private Map<String, Object> filaSuscripcion(UUID tiendaId) {
        return jdbc.queryForMap("SELECT s.\"Estado\", s.\"CicloFacturacion\", s.\"PrecioPactado\", "
                + "s.\"FechaInicio\", s.\"FinPeriodoActual\", p.\"Codigo\" "
                + "FROM \"SuscripcionesTienda\" s JOIN \"PlanesSuscripcion\" p ON p.\"Id\" = s.\"PlanId\" "
                + "WHERE s.\"TiendaId\" = ?", tiendaId);
    }

    @Test
    void sinPlanCreaLaPruebaDeBasicoConFinALosCatorceDias() {
        UUID tienda = nuevaTienda();

        SuscripcionVigente vigente = enTransaccion(() -> suscripciones.crearPrueba(tienda, null));

        Map<String, Object> fila = filaSuscripcion(tienda);
        assertThat(fila.get("Estado")).isEqualTo("PRUEBA");
        assertThat(fila.get("CicloFacturacion")).isEqualTo("MENSUAL");
        assertThat(fila.get("Codigo")).isEqualTo("BASICO");
        assertThat((BigDecimal) fila.get("PrecioPactado")).isEqualByComparingTo("29.00");

        Instant inicio = ((Timestamp) fila.get("FechaInicio")).toInstant();
        Instant fin = ((Timestamp) fila.get("FinPeriodoActual")).toInstant();
        assertThat(Duration.between(inicio, fin)).isEqualTo(Duration.ofDays(14));

        assertThat(vigente.estado()).isEqualTo(EstadoSuscripcion.PRUEBA);
        assertThat(vigente.codigoPlan()).isEqualTo("BASICO");
        assertThat(vigente.limiteProductos()).isEqualTo(100);
        assertThat(vigente.limitePersonal()).isEqualTo(2);
        assertThat(vigente.permiteOperar()).isTrue();
    }

    @Test
    void normalizaElCodigoDelPlanAMayusculas() {
        UUID tienda = nuevaTienda();

        enTransaccion(() -> suscripciones.crearPrueba(tienda, "estandar"));

        assertThat(filaSuscripcion(tienda).get("Codigo")).isEqualTo("ESTANDAR");
    }

    @Test
    void codigoInexistenteONoPublicoDaSus015() {
        UUID tienda = nuevaTienda();
        String noPublico = codigoPlanUnico();
        insertarPlan(noPublico, false, false, 14);

        assertThatThrownBy(() -> enTransaccion(() -> suscripciones.crearPrueba(tienda, "NO-EXISTE")))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("SUS-015");
        assertThatThrownBy(() -> enTransaccion(() -> suscripciones.crearPrueba(tienda, noPublico)))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("SUS-015");
    }

    @Test
    void planConCeroDiasDePruebaDaSus015() {
        UUID tienda = nuevaTienda();
        String sinPrueba = codigoPlanUnico();
        insertarPlan(sinPrueba, true, false, 0);

        assertThatThrownBy(() -> enTransaccion(() -> suscripciones.crearPrueba(tienda, sinPrueba)))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("SUS-015");
    }

    @Test
    void crearPruebaDosVecesParaLaMismaTiendaDaSus002() {
        UUID tienda = nuevaTienda();
        enTransaccion(() -> suscripciones.crearPrueba(tienda, null));

        assertThatThrownBy(() -> enTransaccion(() -> suscripciones.crearPrueba(tienda, null)))
                .isInstanceOf(ConflictoEstadoException.class)
                .extracting("codigo").isEqualTo("SUS-002");
    }

    @Test
    void crearPruebaFueraDeTransaccionFalla() {
        UUID tienda = nuevaTienda();

        assertThatThrownBy(() -> suscripciones.crearPrueba(tienda, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void propietarioYaUsoPruebaSigueSiendoVerdaderoTrasEliminarSuTienda() {
        UUID propietario = crearUsuarioActivo(false);
        assertThat(suscripciones.propietarioYaUsoPrueba(propietario)).isFalse();

        UUID tienda = crearTienda(propietario);
        enTransaccion(() -> suscripciones.crearPrueba(tienda, null));
        assertThat(suscripciones.propietarioYaUsoPrueba(propietario)).isTrue();

        eliminarTienda(tienda);
        assertThat(suscripciones.propietarioYaUsoPrueba(propietario)).isTrue();
    }

    @Test
    void vigenteDevuelveEstadoYLimitesDeLaPrueba() {
        UUID tienda = nuevaTienda();
        enTransaccion(() -> suscripciones.crearPrueba(tienda, null));

        Optional<SuscripcionVigente> vigente = suscripciones.vigente(tienda);

        assertThat(vigente).isPresent();
        assertThat(vigente.get().estado()).isEqualTo(EstadoSuscripcion.PRUEBA);
        assertThat(vigente.get().codigoPlan()).isEqualTo("BASICO");
        assertThat(vigente.get().limiteProductos()).isEqualTo(100);
        assertThat(vigente.get().limitePersonal()).isEqualTo(2);
        assertThat(vigente.get().permiteOperar()).isTrue();
    }

    @Test
    void vigenteEstaVacioSiLaTiendaNoTieneSuscripcion() {
        assertThat(suscripciones.vigente(nuevaTienda())).isEmpty();
    }

    @Test
    void pendienteDePagoCreaLaFilaVencidaConPeriodoVacio() {
        UUID tienda = nuevaTienda();

        SuscripcionVigente vigente = enTransaccion(() -> suscripciones.crearPendienteDePago(tienda, "estandar"));

        Map<String, Object> fila = jdbc.queryForMap("SELECT s.\"Estado\", s.\"CicloFacturacion\", s.\"PrecioPactado\", "
                + "s.\"FechaInicio\", s.\"InicioPeriodoActual\", s.\"FinPeriodoActual\", s.\"FechaVencimiento\", "
                + "s.\"FechaFinPrueba\", s.\"MotivoEstado\", p.\"Codigo\", p.\"PrecioMensual\" "
                + "FROM \"SuscripcionesTienda\" s JOIN \"PlanesSuscripcion\" p ON p.\"Id\" = s.\"PlanId\" "
                + "WHERE s.\"TiendaId\" = ?", tienda);
        assertThat(fila.get("Estado")).isEqualTo("VENCIDA");
        assertThat(fila.get("CicloFacturacion")).isEqualTo("MENSUAL");
        assertThat(fila.get("Codigo")).isEqualTo("ESTANDAR");
        assertThat((BigDecimal) fila.get("PrecioPactado")).isEqualByComparingTo((BigDecimal) fila.get("PrecioMensual"));
        assertThat(fila.get("FechaFinPrueba")).isNull();
        assertThat(fila.get("MotivoEstado")).isEqualTo("Prueba gratuita ya utilizada; requiere pago");
        Instant inicio = ((Timestamp) fila.get("InicioPeriodoActual")).toInstant();
        assertThat(((Timestamp) fila.get("FinPeriodoActual")).toInstant()).isEqualTo(inicio);
        assertThat(((Timestamp) fila.get("FechaVencimiento")).toInstant()).isEqualTo(inicio);
        assertThat(((Timestamp) fila.get("FechaInicio")).toInstant()).isEqualTo(inicio);

        assertThat(vigente.estado()).isEqualTo(EstadoSuscripcion.VENCIDA);
        Optional<SuscripcionVigente> leida = suscripciones.vigente(tienda);
        assertThat(leida).isPresent();
        assertThat(leida.get().permiteOperar()).isFalse();
    }

    @Test
    void pendienteDePagoNoCuentaComoPruebaUsada() {
        UUID propietario = crearUsuarioActivo(false);
        UUID tienda = crearTienda(propietario);

        enTransaccion(() -> suscripciones.crearPendienteDePago(tienda, null));

        assertThat(suscripciones.propietarioYaUsoPrueba(propietario)).isFalse();
    }

    @Test
    void pendienteDePagoAceptaUnPlanSinDiasDePruebaPeroNoUnoInexistente() {
        UUID tienda = nuevaTienda();
        String sinPrueba = codigoPlanUnico();
        insertarPlan(sinPrueba, true, false, 0);

        enTransaccion(() -> suscripciones.crearPendienteDePago(tienda, sinPrueba));
        assertThat(filaSuscripcion(tienda).get("Estado")).isEqualTo("VENCIDA");
        // el plan de prueba se borra al terminar: la suscripción que lo referencia debe irse antes
        jdbc.update("DELETE FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ?", tienda);

        UUID otra = nuevaTienda();
        assertThatThrownBy(() -> enTransaccion(() -> suscripciones.crearPendienteDePago(otra, "NO-EXISTE")))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("SUS-015");
    }

    @Test
    void pendienteDePagoRespetaSus002() {
        UUID tienda = nuevaTienda();
        enTransaccion(() -> suscripciones.crearPendienteDePago(tienda, null));

        assertThatThrownBy(() -> enTransaccion(() -> suscripciones.crearPendienteDePago(tienda, null)))
                .isInstanceOf(ConflictoEstadoException.class)
                .extracting("codigo").isEqualTo("SUS-002");
    }
}
