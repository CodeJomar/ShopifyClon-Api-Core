package shopify.api.core.modulos.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.modulos.envio.modelo.TarifaAplicable;
import shopify.api.core.modulos.envio.servicio.TarifaEnvioServicio;

class TarifaEnvioServicioTest extends PruebaEnvioBase {

    @Autowired
    private TarifaEnvioServicio tarifas;

    @Test
    void buscarActivaSoloAceptaTarifasActivasDeLaPropiaTienda() {
        UUID tienda = crearTienda(nuevaSesion().id());
        UUID otra = crearTienda(nuevaSesion().id());
        UUID activa = insertarTarifa(tienda, "Estándar", "5.00", true, 0, false);
        UUID inactiva = insertarTarifa(tienda, "Inactiva", "3.00", false, 0, false);
        UUID eliminada = insertarTarifa(tienda, "Eliminada", "2.00", true, 0, true);
        UUID ajena = insertarTarifa(otra, "Ajena", "4.00", true, 0, false);

        TarifaAplicable aplicable = tarifas.buscarActiva(tienda, activa);
        assertThat(aplicable.id()).isEqualTo(activa);
        assertThat(aplicable.nombre()).isEqualTo("Estándar");
        assertThat(aplicable.precio()).isEqualByComparingTo(new BigDecimal("5.00"));

        for (UUID noValida : List.of(inactiva, eliminada, ajena, UUID.randomUUID())) {
            assertThatThrownBy(() -> tarifas.buscarActiva(tienda, noValida))
                    .isInstanceOf(ConflictoEstadoException.class)
                    .extracting("codigo").isEqualTo("ENV-001");
        }
    }

    @Test
    void tieneTarifasActivasSigueLaExistenciaDeTarifasActivas() {
        UUID tienda = crearTienda(nuevaSesion().id());
        assertThat(tarifas.tieneTarifasActivas(tienda)).isFalse();

        insertarTarifa(tienda, "Inactiva", "3.00", false, 0, false);
        assertThat(tarifas.tieneTarifasActivas(tienda)).isFalse();

        insertarTarifa(tienda, "Activa", "5.00", true, 0, false);
        assertThat(tarifas.tieneTarifasActivas(tienda)).isTrue();
    }
}
