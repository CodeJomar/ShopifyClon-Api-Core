package shopify.api.core.modulos.suscripcion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Utilidades para las pruebas del módulo suscripción. */
public abstract class PruebaSuscripcionBase extends PruebaIntegracionBase {

    /** Los planes de prueba se borran al final de cada prueba: la base es compartida por todas las clases. */
    private final List<String> planesDePrueba = new ArrayList<>();

    @Autowired
    protected TransactionTemplate transaccion;

    /** Los métodos {@code MANDATORY} del servicio necesitan una transacción abierta. */
    protected <T> T enTransaccion(Supplier<T> accion) {
        return transaccion.execute(estado -> accion.get());
    }

    /** Inserta un plan directamente en la base; {@code codigo} debe ser único y en mayúsculas. */
    protected void insertarPlan(String codigo, boolean esPublico, boolean eliminado, int diasPrueba) {
        jdbc.update("INSERT INTO \"PlanesSuscripcion\" (\"Codigo\", \"Nombre\", \"PrecioMensual\", \"PrecioAnual\", "
                + "\"LimitePersonal\", \"PorcentajeComision\", \"DiasPrueba\", \"EsPublico\", \"Eliminado\") "
                + "VALUES (?, 'Plan de prueba', 10.00, 100.00, 1, 0.0100, ?, ?, ?)",
                codigo, diasPrueba, esPublico, eliminado);
        planesDePrueba.add(codigo);
    }

    /** Para planes creados por la API: se borran al final de la prueba, igual que los insertados. */
    protected void recordarPlan(String codigo) {
        planesDePrueba.add(codigo);
    }

    protected String codigoPlanUnico() {
        return "P" + sufijo().toUpperCase();
    }

    @AfterEach
    void eliminarPlanesDePrueba() {
        planesDePrueba.forEach(codigo ->
                jdbc.update("DELETE FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = ?", codigo));
        planesDePrueba.clear();
    }
}
