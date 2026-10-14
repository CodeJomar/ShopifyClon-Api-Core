package shopify.api.core.modulos.suscripcion.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.modelo.UsoTienda;
import shopify.api.core.modulos.suscripcion.repositorio.SuscripcionTiendaRepositorio;

/** Uso de la tienda frente a los límites de un plan (SUS-008). */
@Service
@RequiredArgsConstructor
public class UsoPlanServicio {

    private final SuscripcionTiendaRepositorio suscripciones;

    public UsoTienda uso(UUID tiendaId) {
        return new UsoTienda(suscripciones.contarProductos(tiendaId), suscripciones.contarPersonalSinPropietario(tiendaId));
    }

    /** Lanza SUS-008 con cuánto sobra si el uso actual no cabe en el plan destino. */
    public void exigirDentroDeLimites(UUID tiendaId, PlanSuscripcionEntidad plan) {
        UsoTienda uso = uso(tiendaId);
        if (plan.getLimiteProductos() != null && uso.productos() > plan.getLimiteProductos()) {
            long sobra = uso.productos() - plan.getLimiteProductos();
            throw new ReglaNegocioException("SUS-008", "Tu tienda tiene " + uso.productos() + " productos y el plan "
                    + plan.getNombre() + " permite " + plan.getLimiteProductos() + ". Desactiva " + sobra
                    + " productos antes de cambiar");
        }
        if (uso.personal() > plan.getLimitePersonal()) {
            long sobra = uso.personal() - plan.getLimitePersonal();
            throw new ReglaNegocioException("SUS-008", "Tu tienda tiene " + uso.personal() + " miembros de personal y el plan "
                    + plan.getNombre() + " permite " + plan.getLimitePersonal() + ". Quita " + sobra
                    + " antes de cambiar");
        }
    }
}
