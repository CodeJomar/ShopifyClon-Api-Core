package shopify.api.core.modulos.resena.servicio;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.resena.dto.request.ResenaRequests.ResenaRequest;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.Calificacion;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.PaginaResenas;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.Resena;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.ResenasProducto;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.Resumen;
import shopify.api.core.modulos.resena.entidad.ResenaEntidad;
import shopify.api.core.modulos.resena.repositorio.ResenaConsultaRepositorio;
import shopify.api.core.modulos.resena.repositorio.ResenaRepositorio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * Reseñas y calificaciones de productos (R-011). Reglas: una por persona y producto; solo con una compra pagada
 * del producto (RES-001); no reseñan los miembros de la tienda (RES-002); la segunda es RES-003. Una reseña oculta
 * por moderación o eliminada no se muestra ni cuenta en el promedio.
 */
@Service
@RequiredArgsConstructor
public class ResenaServicio {

    static final int TAMANO_PAGINA = 10;

    private final ResenaRepositorio resenas;
    private final ResenaConsultaRepositorio consulta;
    private final TiendaServicio tiendas;
    private final AuditoriaServicio auditoria;

    // ------------------------------------------------------------------ lectura

    /** Resumen, mi reseña, si puedo reseñar y una página de reseñas. Sin sesión solo cambian los dos campos personales. */
    @Transactional(readOnly = true)
    public ResenasProducto listar(String subdominio, String slug, String orden, Integer pagina) {
        UUID tiendaId = tienda(subdominio);
        UUID productoId = producto(tiendaId, slug);
        int numero = pagina == null ? 0 : pagina;
        if (numero < 0) {
            throw new SolicitudInvalidaException("VAL-003", "La página debe ser 0 o más");
        }
        String ordenSql = ordenSql(orden);

        Map<String, Object> resumen = consulta.resumen(productoId);
        long total = ((Number) resumen.get("total")).longValue();
        Map<String, Long> distribucion = new LinkedHashMap<>();
        for (int estrellas = 1; estrellas <= 5; estrellas++) {
            distribucion.put(String.valueOf(estrellas), ((Number) resumen.get("c" + estrellas)).longValue());
        }
        BigDecimal promedio = total == 0 ? null : new BigDecimal(resumen.get("promedio").toString());

        List<Resena> datos = consulta.lista(productoId, ordenSql, TAMANO_PAGINA, (long) numero * TAMANO_PAGINA).stream()
                .map(ResenaServicio::aResena).toList();
        PaginaResenas paginaResenas = new PaginaResenas(datos, total, numero, (int) Math.ceil(total / (double) TAMANO_PAGINA));

        Optional<UUID> usuario = SesionActual.usuarioIdOpcional();
        Resena mia = usuario.flatMap(u -> consulta.deUsuario(productoId, u)).map(ResenaServicio::aResena).orElse(null);
        boolean puede = usuario.isPresent() && mia == null && !consulta.esMiembro(usuario.get(), tiendaId)
                && !tieneResenaPropia(productoId, usuario.get()) && consulta.compro(usuario.get(), productoId);
        return new ResenasProducto(new Resumen(promedio, total, distribucion), mia, puede, paginaResenas);
    }

    /** Calificación de varios productos (tarjetas del mercado y de la vitrina); sin reseñas no aparecen en el mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, Calificacion> calificaciones(Collection<UUID> productoIds) {
        Map<UUID, Calificacion> resultado = new HashMap<>();
        consulta.resumenes(productoIds).forEach((id, fila) -> resultado.put(id,
                new Calificacion(new BigDecimal(fila.get("promedio").toString()), ((Number) fila.get("total")).longValue())));
        return resultado;
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public Resena crear(String subdominio, String slug, ResenaRequest solicitud) {
        UUID usuarioId = SesionActual.usuarioId();
        UUID tiendaId = tienda(subdominio);
        UUID productoId = producto(tiendaId, slug);
        if (consulta.esMiembro(usuarioId, tiendaId)) {
            throw new AccesoDenegadoException("RES-002", "No puedes reseñar productos de una tienda en la que trabajas");
        }
        if (!consulta.compro(usuarioId, productoId)) {
            throw new AccesoDenegadoException("RES-001", "Solo puedes reseñar un producto que compraste y pagaste");
        }
        if (tieneResenaPropia(productoId, usuarioId)) {
            throw new ConflictoEstadoException("RES-003", "Ya reseñaste este producto: edita tu reseña");
        }
        ResenaEntidad resena = new ResenaEntidad();
        resena.setProductoId(productoId);
        resena.setTiendaId(tiendaId);
        resena.setUsuarioId(usuarioId);
        resena.setCalificacion(solicitud.calificacion());
        resena.setComentario(comentario(solicitud.comentario()));
        try {
            resenas.saveAndFlush(resena);
        } catch (DataIntegrityViolationException ex) {
            // Dos peticiones a la vez: gana una (UxResenasProductoUsuario)
            throw new ConflictoEstadoException("RES-003", "Ya reseñaste este producto: edita tu reseña");
        }
        return aResena(resena);
    }

    /** Solo la autora; para cualquier otra persona la reseña no existe (404). */
    @Transactional
    public Resena editar(UUID resenaId, ResenaRequest solicitud) {
        ResenaEntidad resena = propia(resenaId);
        if (Boolean.TRUE.equals(resena.getOculta())) {
            throw new ConflictoEstadoException("RES-005", "La moderación ocultó tu reseña: ya no se puede editar");
        }
        resena.setCalificacion(solicitud.calificacion());
        resena.setComentario(comentario(solicitud.comentario()));
        return aResena(resenas.saveAndFlush(resena));
    }

    /** Borrado lógico: la autora puede volver a reseñar el producto. */
    @Transactional
    public void eliminar(UUID resenaId) {
        ResenaEntidad resena = propia(resenaId);
        resena.setEliminado(true);
        resenas.save(resena);
    }

    /** Moderación de plataforma: oculta la reseña (deja de contar) y queda auditada. */
    @Transactional
    public void ocultar(UUID resenaId, String motivo) {
        ResenaEntidad resena = resenas.findFirstByIdAndEliminadoFalse(resenaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("RES-404", "No encontramos esa reseña"));
        if (Boolean.TRUE.equals(resena.getOculta())) {
            return;
        }
        resena.setOculta(true);
        resena.setMotivoOcultacion(motivo.strip());
        resenas.saveAndFlush(resena);
        auditoria.registrar(resena.getTiendaId(), AccionAuditoria.RESENA_OCULTADA, "ResenasProducto", resenaId, motivo.strip(),
                Map.of("producto_id", resena.getProductoId().toString(), "usuario_id", resena.getUsuarioId().toString()));
    }

    // ------------------------------------------------------------------ utilidades

    private ResenaEntidad propia(UUID resenaId) {
        UUID usuarioId = SesionActual.usuarioId();
        return resenas.findFirstByIdAndEliminadoFalse(resenaId)
                .filter(r -> r.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("RES-404", "No encontramos esa reseña"));
    }

    private boolean tieneResenaPropia(UUID productoId, UUID usuarioId) {
        return resenas.findFirstByProductoIdAndUsuarioIdAndEliminadoFalse(productoId, usuarioId).isPresent();
    }

    private UUID tienda(String subdominio) {
        return tiendas.idOperativaPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
    }

    private UUID producto(UUID tiendaId, String slug) {
        String identificador = slug == null ? "" : slug.strip().toLowerCase(Locale.ROOT);
        return consulta.productoPublicado(tiendaId, identificador)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAT-404", "No encontramos ese producto"));
    }

    private static String ordenSql(String orden) {
        String valor = orden == null || orden.isBlank() ? "recientes" : orden.strip().toLowerCase(Locale.ROOT);
        return switch (valor) {
            case "recientes" -> "r.\"FechaCreacion\" DESC, r.\"Id\"";
            case "mejores" -> "r.\"Calificacion\" DESC, r.\"FechaCreacion\" DESC, r.\"Id\"";
            case "peores" -> "r.\"Calificacion\" ASC, r.\"FechaCreacion\" DESC, r.\"Id\"";
            default -> throw new SolicitudInvalidaException("VAL-003", "El orden debe ser recientes, mejores o peores");
        };
    }

    private static String comentario(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /** "Ana P.": el primer nombre y la inicial del apellido. Nunca el correo. */
    private static String autor(String nombres, String apellidos) {
        String primero = nombres == null || nombres.isBlank() ? "" : nombres.strip().split("\\s+")[0];
        String inicial = apellidos == null || apellidos.isBlank() ? "" : " " + apellidos.strip().substring(0, 1).toUpperCase(Locale.ROOT) + ".";
        return (primero + inicial).strip();
    }

    private static Resena aResena(Map<String, Object> fila) {
        Object fecha = fila.get("FechaCreacion");
        Instant instante = fecha instanceof Timestamp t ? t.toInstant() : Instant.parse(fecha.toString());
        return new Resena((UUID) fila.get("Id"), ((Number) fila.get("Calificacion")).intValue(), (String) fila.get("Comentario"),
                autor((String) fila.get("Nombres"), (String) fila.get("Apellidos")), true, instante);
    }

    /** Tras crear o editar: el autor es quien está en sesión, así que se lee su nombre aparte. */
    private Resena aResena(ResenaEntidad resena) {
        Map<String, Object> fila = consulta.deUsuario(resena.getProductoId(), resena.getUsuarioId()).orElse(null);
        String autor = fila == null ? null : autor((String) fila.get("Nombres"), (String) fila.get("Apellidos"));
        return new Resena(resena.getId(), resena.getCalificacion(), resena.getComentario(), autor, true, resena.getFechaCreacion());
    }
}
