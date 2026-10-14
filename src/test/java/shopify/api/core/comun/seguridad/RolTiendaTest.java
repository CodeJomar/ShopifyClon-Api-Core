package shopify.api.core.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Matriz rol × permiso de {@code docs/negocio/01-actores-roles-permisos.md}, caso por caso. */
class RolTiendaTest {

    // Orden de columnas: PROPIETARIO, ADMINISTRADOR, GESTOR_CATALOGO, GESTOR_PEDIDOS
    private static final Map<Permiso, boolean[]> MATRIZ = Map.ofEntries(
            Map.entry(Permiso.TIENDA_CONFIGURAR, new boolean[]{true, true, false, false}),
            Map.entry(Permiso.SUSCRIPCION_GESTIONAR, new boolean[]{true, false, false, false}),
            Map.entry(Permiso.PERSONAL_GESTIONAR, new boolean[]{true, true, false, false}),
            Map.entry(Permiso.CATALOGO_GESTIONAR, new boolean[]{true, true, true, false}),
            Map.entry(Permiso.INVENTARIO_AJUSTAR, new boolean[]{true, true, true, false}),
            Map.entry(Permiso.CUPONES_GESTIONAR, new boolean[]{true, true, false, false}),
            Map.entry(Permiso.ENVIOS_CONFIGURAR, new boolean[]{true, true, false, false}),
            Map.entry(Permiso.ORDENES_VER, new boolean[]{true, true, false, true}),
            Map.entry(Permiso.ORDENES_GESTIONAR, new boolean[]{true, true, false, true}),
            Map.entry(Permiso.ORDENES_CANCELAR, new boolean[]{true, true, false, true}),
            Map.entry(Permiso.REEMBOLSOS_EMITIR, new boolean[]{true, true, false, false}),
            Map.entry(Permiso.METRICAS_VER, new boolean[]{true, true, false, false}));

    private static final RolTienda[] ROLES = {
            RolTienda.PROPIETARIO, RolTienda.ADMINISTRADOR, RolTienda.GESTOR_CATALOGO, RolTienda.GESTOR_PEDIDOS};

    static Stream<Arguments> casos() {
        return Stream.of(Permiso.values()).flatMap(permiso -> {
            boolean[] fila = MATRIZ.get(permiso);
            return Stream.of(0, 1, 2, 3).map(i -> Arguments.of(ROLES[i], permiso, fila[i]));
        });
    }

    @Test
    void laMatrizCubreTodosLosPermisos() {
        assertThat(MATRIZ.keySet()).containsExactlyInAnyOrder(Permiso.values());
    }

    @ParameterizedTest(name = "{0} · {1} = {2}")
    @MethodSource("casos")
    void cadaCeldaDeLaMatriz(RolTienda rol, Permiso permiso, boolean esperado) {
        assertThat(rol.tiene(permiso)).isEqualTo(esperado);
    }

    @Test
    void desdeCodigoReconoceLosRolesDeTiendaYRechazaElResto() {
        assertThat(RolTienda.desdeCodigo("GESTOR_PEDIDOS")).contains(RolTienda.GESTOR_PEDIDOS);
        assertThat(RolTienda.desdeCodigo("CLIENTE")).isEmpty();
        assertThat(RolTienda.desdeCodigo(null)).isEmpty();
    }
}
