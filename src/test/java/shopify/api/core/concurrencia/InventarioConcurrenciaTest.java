package shopify.api.core.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ExcepcionNegocio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;

/** Reserva y ajuste simultáneos sobre la misma variante: sin cantidades negativas y con movimientos coherentes. */
class InventarioConcurrenciaTest extends PruebaConcurrenciaBase {

    @Autowired
    private InventarioServicio inventario;

    @Autowired
    private TransactionTemplate transaccion;

    @BeforeEach
    void preparar() {
        prepararTienda();
    }

    @Test
    void reservaYAjusteSimultaneosNoDejanCantidadesNegativasYMovimientosCoherentes() throws Exception {
        UUID variante = variante(new BigDecimal("10.00"), 5, false);

        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> transaccion.execute(estado -> {
                    inventario.reservar(tienda, List.of(new LineaInventario(variante, 2)), null);
                    return "RESERVA";
                }),
                () -> transaccion.execute(estado -> {
                    inventario.ajustar(tienda, variante, 1, "Conteo físico");
                    return "AJUSTE";
                })));

        // Cada operación puede aprobarse o rechazarse por regla de negocio (INV-002 / INV-007), nunca por error técnico
        assertThat(resultados).allMatch(r -> r.ok() || r.error() instanceof ExcepcionNegocio);
        assertThat(resultados).anyMatch(Simultaneo.Resultado::ok);
        int disponible = disponible(variante);
        int reservada = reservada(variante);
        assertThat(disponible).isGreaterThanOrEqualTo(0);
        assertThat(reservada).isGreaterThanOrEqualTo(0);
        // El último movimiento refleja las cantidades finales
        Map<String, Object> ultimo = jdbc.queryForMap("SELECT \"DisponibleResultante\", \"ReservadoResultante\" "
                + "FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? ORDER BY \"FechaCreacion\" DESC LIMIT 1", variante);
        assertThat(ultimo.get("DisponibleResultante")).isEqualTo(disponible);
        assertThat(ultimo.get("ReservadoResultante")).isEqualTo(reservada);
    }
}
