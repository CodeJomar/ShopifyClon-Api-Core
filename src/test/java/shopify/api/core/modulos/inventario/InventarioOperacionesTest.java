package shopify.api.core.modulos.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;

class InventarioOperacionesTest extends PruebaInventarioBase {

    @Autowired
    private InventarioServicio inventario;

    @Autowired
    private TransactionTemplate transaccion;

    private void enTransaccion(Runnable accion) {
        transaccion.executeWithoutResult(estado -> accion.run());
    }

    private static List<LineaInventario> linea(UUID variante, int cantidad) {
        return List.of(new LineaInventario(variante, cantidad));
    }

    @Test
    void reservarConfirmarYLiberarDejanLasCantidadesCorrectas() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID v = variante(tienda, true, 5, 0, false);
        UUID orden = null;

        enTransaccion(() -> inventario.reservar(tienda, linea(v, 2), orden));
        assertThat(disponible(v)).isEqualTo(5);
        assertThat(reservada(v)).isEqualTo(2);
        assertThat(movimientos(v, "RESERVA")).isEqualTo(1);

        enTransaccion(() -> inventario.confirmarVenta(tienda, linea(v, 2), orden));
        assertThat(disponible(v)).isEqualTo(3);
        assertThat(reservada(v)).isZero();
        assertThat(movimientos(v, "VENTA")).isEqualTo(1);

        UUID otra = variante(tienda, true, 5, 0, false);
        enTransaccion(() -> inventario.reservar(tienda, linea(otra, 2), orden));
        enTransaccion(() -> inventario.liberar(tienda, linea(otra, 2), orden));
        assertThat(disponible(otra)).isEqualTo(5);
        assertThat(reservada(otra)).isZero();
        assertThat(movimientos(otra, "LIBERACION")).isEqualTo(1);
    }

    @Test
    void reservarMasDelVendibleEs409INV002SinCambiarNada() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID holgada = variante(tienda, true, 5, 0, false);
        UUID corta = variante(tienda, true, 1, 0, false);

        assertThatThrownBy(() -> enTransaccion(() -> inventario.reservar(tienda, List.of(
                new LineaInventario(holgada, 3), new LineaInventario(corta, 2)), null)))
                .isInstanceOfSatisfying(ConflictoEstadoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("INV-002");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> faltantes = (List<Map<String, Object>>) ex.getDetalles().get("variantes");
                    assertThat(faltantes).hasSize(1);
                    assertThat(faltantes.get(0).get("variante_id")).isEqualTo(corta);
                    assertThat(faltantes.get(0).get("solicitado")).isEqualTo(2);
                    assertThat(faltantes.get(0).get("disponible")).isEqualTo(1);
                });

        assertThat(reservada(holgada)).isZero();
        assertThat(reservada(corta)).isZero();
        assertThat(movimientosTotales(holgada)).isZero();
    }

    @Test
    void todasLasVariantesCortasSeListanDeUnaVez() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID a = variante(tienda, true, 1, 0, false);
        UUID b = variante(tienda, true, 0, 0, false);

        assertThatThrownBy(() -> enTransaccion(() -> inventario.reservar(tienda, List.of(
                new LineaInventario(a, 2), new LineaInventario(b, 1)), null)))
                .isInstanceOfSatisfying(ConflictoEstadoException.class, ex -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> faltantes = (List<Map<String, Object>>) ex.getDetalles().get("variantes");
                    assertThat(faltantes).hasSize(2);
                });
    }

    @Test
    void conVentaSinStockSiempreReserva() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID v = variante(tienda, true, 0, 0, true);

        enTransaccion(() -> inventario.reservar(tienda, linea(v, 3), null));

        assertThat(reservada(v)).isEqualTo(3);
    }

    @Test
    void varianteQueNoRastreaNoCreaMovimientos() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID v = variante(tienda, false, 0, 0, false);

        enTransaccion(() -> inventario.reservar(tienda, linea(v, 5), null));
        enTransaccion(() -> inventario.confirmarVenta(tienda, linea(v, 5), null));
        enTransaccion(() -> inventario.reponer(tienda, linea(v, 2), null, "Reposición"));
        enTransaccion(() -> inventario.liberar(tienda, linea(v, 1), null));

        assertThat(movimientosTotales(v)).isZero();
        assertThat(disponible(v)).isZero();
        assertThat(reservada(v)).isZero();
    }

    @Test
    void reponerSumaYDejaMovimientoConMotivo() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID v = variante(tienda, true, 1, 0, false);

        enTransaccion(() -> inventario.reponer(tienda, linea(v, 4), null, "Llegó el proveedor"));

        assertThat(disponible(v)).isEqualTo(5);
        assertThat(movimientos(v, "REPOSICION")).isEqualTo(1);
    }

    @Test
    void varianteDeOtraTiendaOSinFilaEsINV404() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID ajena = variante(crearTienda(crearUsuarioActivo(false)), true, 5, 0, false);

        assertThatThrownBy(() -> enTransaccion(() -> inventario.reservar(tienda, linea(ajena, 1), null)))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("INV-404"));
        assertThat(reservada(ajena)).isZero();
    }

    /** Dos hilos reservan la última unidad a la vez: exactamente uno lo logra (INV-002 para el otro). */
    @Test
    void dosHilosNoPuedenVenderLaUltimaUnidad() throws Exception {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID v = variante(tienda, true, 1, 0, false);
        CountDownLatch arranque = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> intento = () -> {
                arranque.await();
                try {
                    enTransaccion(() -> inventario.reservar(tienda, linea(v, 1), null));
                    return "OK";
                } catch (ConflictoEstadoException ex) {
                    return ex.getCodigo();
                }
            };
            Future<String> primero = pool.submit(intento);
            Future<String> segundo = pool.submit(intento);
            arranque.countDown();

            List<String> resultados = new ArrayList<>();
            resultados.add(primero.get(60, TimeUnit.SECONDS));
            resultados.add(segundo.get(60, TimeUnit.SECONDS));
            assertThat(resultados).containsExactlyInAnyOrder("OK", "INV-002");
            assertThat(reservada(v)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Dos hilos piden las mismas dos variantes en orden inverso. El bloqueo ordenado por VarianteId evita el
     * interbloqueo: ambos terminan y reservan lo que pidieron.
     */
    @Test
    void pedidosCruzadosSobreDosVariantesNoSeInterbloquean() throws Exception {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        UUID a = variante(tienda, true, 2, 0, false);
        UUID b = variante(tienda, true, 2, 0, false);
        CountDownLatch arranque = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> uno = pool.submit(() -> {
                arranque.await();
                enTransaccion(() -> inventario.reservar(tienda,
                        List.of(new LineaInventario(a, 1), new LineaInventario(b, 1)), null));
                return null;
            });
            Future<?> dos = pool.submit(() -> {
                arranque.await();
                enTransaccion(() -> inventario.reservar(tienda,
                        List.of(new LineaInventario(b, 1), new LineaInventario(a, 1)), null));
                return null;
            });
            arranque.countDown();
            uno.get(60, TimeUnit.SECONDS);
            dos.get(60, TimeUnit.SECONDS);

            assertThat(reservada(a)).isEqualTo(2);
            assertThat(reservada(b)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
