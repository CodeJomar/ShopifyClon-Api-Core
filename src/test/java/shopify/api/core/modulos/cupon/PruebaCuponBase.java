package shopify.api.core.modulos.cupon;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Utilidades de las pruebas del módulo cupón: usuarios con token, tiendas y cupones preparados en la base. */
abstract class PruebaCuponBase extends PruebaIntegracionBase {

    @Autowired
    protected TransactionTemplate transaccion;

    /** Usuario activo con su token de sesión. */
    protected record Sesion(UUID id, String correo, String token) {
    }

    protected Sesion nuevaSesion() {
        UUID id = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
        return new Sesion(id, correo, tokenPara(id, correo));
    }

    protected UUID nuevaTienda() {
        return crearTienda(nuevaSesion().id());
    }

    /** Los métodos {@code MANDATORY} del servicio necesitan una transacción abierta. */
    protected <T> T enTransaccion(Supplier<T> accion) {
        return transaccion.execute(estado -> accion.get());
    }

    protected void enTransaccionSinResultado(Runnable accion) {
        transaccion.executeWithoutResult(estado -> accion.run());
    }

    protected Map<String, Object> cuponJson(String codigo, String tipo, Object valor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("tipo_descuento", tipo);
        cuerpo.put("valor_descuento", valor);
        return cuerpo;
    }

    /** Inserta un cupón directamente en la base y devuelve su id. */
    protected UUID insertarCupon(UUID tiendaId, String codigo, String tipo, BigDecimal valor, Integer limite,
                                 boolean activo, Instant inicio, Instant fin) {
        return jdbc.queryForObject("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", "
                        + "\"ValorDescuento\", \"LimiteUsosTotal\", \"EstaActivo\", \"FechaInicio\", \"FechaFin\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING \"Id\"", UUID.class,
                tiendaId, codigo, tipo, valor, limite, activo, Timestamp.from(inicio),
                fin == null ? null : Timestamp.from(fin));
    }

    protected int usos(UUID cuponId) {
        return jdbc.queryForObject("SELECT \"CantidadUsosActual\" FROM \"Cupones\" WHERE \"Id\" = ?", Integer.class, cuponId);
    }

    protected void fijarUsos(UUID cuponId, int usos) {
        jdbc.update("UPDATE \"Cupones\" SET \"CantidadUsosActual\" = ? WHERE \"Id\" = ?", usos, cuponId);
    }

    protected static BigDecimal importe(String valor) {
        return new BigDecimal(valor);
    }
}
