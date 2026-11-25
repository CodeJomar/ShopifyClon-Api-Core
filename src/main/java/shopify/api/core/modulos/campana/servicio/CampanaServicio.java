package shopify.api.core.modulos.campana.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.cache.CacheTemporal;
import shopify.api.core.comun.campana.Campana;
import shopify.api.core.comun.campana.PrecioCampana;
import shopify.api.core.comun.campana.TramosCampana;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.CampanaActiva;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.CampanaDisponible;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.CampanasPlataforma;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.Tramo;
import shopify.api.core.modulos.campana.repositorio.CampanaActivaRepositorio;

/**
 * Campaña comercial vigente (R-028). Quien necesite precios con campaña (mercado, vitrina, carrito, checkout) pregunta
 * aquí: {@link #aplicar(BigDecimal)} devuelve el precio de lista, el final y la campaña. La lectura se recuerda unos
 * segundos para no consultar la base en cada producto; al cambiar la campaña se refresca de inmediato.
 */
@Service
@RequiredArgsConstructor
public class CampanaServicio {

    private static final long VIGENCIA_LECTURA_MS = 5_000;

    private final CampanaActivaRepositorio repositorio;
    private final AuditoriaServicio auditoria;
    private final CacheTemporal cacheTemporal;
    private final Clock reloj;

    private volatile Optional<Campana> recordada = Optional.empty();
    private volatile long leidaEn = Long.MIN_VALUE;

    /** La campaña vigente, o vacío si no hay ninguna. */
    public Optional<Campana> activa() {
        long ahora = reloj.millis();
        if (leidaEn == Long.MIN_VALUE || ahora - leidaEn > VIGENCIA_LECTURA_MS) {
            recordada = repositorio.codigoActivo().flatMap(Campana::de);
            leidaEn = ahora;
        }
        return recordada;
    }

    /** Precio con la campaña vigente; sin campaña, el mismo precio sin cambios. */
    public PrecioCampana aplicar(BigDecimal precioLista) {
        return activa().map(c -> PrecioCampana.con(c, precioLista)).orElseGet(() -> PrecioCampana.sinCampana(precioLista));
    }

    public CampanaActiva estadoPublico() {
        Optional<Campana> activa = activa();
        return new CampanaActiva(activa.isPresent(), activa.map(Campana::name).orElse(null),
                activa.map(Campana::nombre).orElse(null), tramos());
    }

    public CampanasPlataforma estadoPlataforma() {
        return new CampanasPlataforma(activa().map(CampanaServicio::disponible).orElse(null),
                Arrays.stream(Campana.values()).map(CampanaServicio::disponible).toList(), tramos());
    }

    /** Cambia la campaña vigente (código nulo o vacío = ninguna). CAM-001 si el código no existe. Queda auditado. */
    @Transactional
    public CampanaActiva cambiar(String codigo) {
        Optional<Campana> nueva = Optional.empty();
        if (codigo != null && !codigo.isBlank()) {
            nueva = Campana.de(codigo);
            if (nueva.isEmpty()) {
                throw new ReglaNegocioException("CAM-001", "La campaña «" + codigo.strip() + "» no existe");
            }
        }
        String anterior = repositorio.codigoActivo().orElse(null);
        repositorio.guardar(nueva.map(Campana::name).orElse(null), SesionActual.usuarioIdOpcional().orElse(null));
        auditoria.registrar(null, AccionAuditoria.CAMPANA_CAMBIADA, "CampanaActiva", null, null,
                Map.of("anterior", anterior == null ? "NINGUNA" : anterior,
                        "nueva", nueva.map(Campana::name).orElse("NINGUNA")));
        recordada = nueva;
        leidaEn = reloj.millis();
        // Las lecturas públicas recordadas (mercado, vitrina) llevan el precio de la campaña anterior
        cacheTemporal.vaciar();
        return estadoPublico();
    }

    private static List<Tramo> tramos() {
        return TramosCampana.TRAMOS.stream().map(t -> new Tramo(t.desde(), t.porcentaje())).toList();
    }

    private static CampanaDisponible disponible(Campana c) {
        return new CampanaDisponible(c.name(), c.nombre());
    }
}
