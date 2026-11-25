package shopify.api.core.modulos.campana.dto.response;

import java.math.BigDecimal;
import java.util.List;

public final class CampanaResponses {

    private CampanaResponses() {
    }

    public record Tramo(BigDecimal desde, int porcentaje) {
    }

    /** Campaña pública: sin campaña vigente {@code codigo} y {@code nombre} no vienen. */
    public record CampanaActiva(boolean activa, String codigo, String nombre, List<Tramo> tramos) {
    }

    public record CampanaDisponible(String codigo, String nombre) {
    }

    /** Vista del administrador: la vigente (si hay), las que puede elegir y los tramos. */
    public record CampanasPlataforma(CampanaDisponible activa, List<CampanaDisponible> disponibles, List<Tramo> tramos) {
    }
}
