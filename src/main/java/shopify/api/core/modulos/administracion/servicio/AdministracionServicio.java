package shopify.api.core.modulos.administracion.servicio;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.Duenio;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.MiembroEquipo;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.PedidoAdmin;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.RegistroAuditoria;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.TiendaAdmin;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.TiendaAdminDetalle;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.TiendaDeUsuario;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.UsuarioAdmin;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.UsuarioAdminDetalle;
import shopify.api.core.modulos.administracion.repositorio.AdministracionRepositorio;

/**
 * Administración de plataforma (R-001, B8): consulta de tiendas, usuarios, auditoría y pedidos. Solo lectura; las
 * acciones sobre usuarios viven en {@code UsuarioAdminServicio}. Paginado de 20; búsquedas con LIKE escapado.
 */
@Service
@RequiredArgsConstructor
public class AdministracionServicio {

    static final int TAMANO_PAGINA = 20;
    private static final int LARGO_MAXIMO_TEXTO = 100;

    private final AdministracionRepositorio repositorio;

    @Transactional(readOnly = true)
    public Page<TiendaAdmin> tiendas(String q, String estado, String plan, int pagina) {
        Boolean activa = switch (texto(estado) == null ? "" : estado.strip().toUpperCase(Locale.ROOT)) {
            case "" -> null;
            case "ACTIVA" -> Boolean.TRUE;
            case "INACTIVA" -> Boolean.FALSE;
            default -> throw new SolicitudInvalidaException("VAL-003", "El estado debe ser ACTIVA o INACTIVA");
        };
        String patron = patron(q);
        String codigoPlan = texto(plan) == null ? null : plan.strip().toUpperCase(Locale.ROOT);
        int p = Math.max(pagina, 0);
        List<TiendaAdmin> filas = repositorio.tiendas(patron, activa, codigoPlan, TAMANO_PAGINA, (long) p * TAMANO_PAGINA)
                .stream().map(AdministracionServicio::aTienda).toList();
        return new PageImpl<>(filas, PageRequest.of(p, TAMANO_PAGINA), repositorio.contarTiendas(patron, activa, codigoPlan));
    }

    @Transactional(readOnly = true)
    public TiendaAdminDetalle tienda(UUID id) {
        Map<String, Object> fila = repositorio.tienda(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "No encontramos esa tienda"));
        List<MiembroEquipo> equipo = repositorio.equipo(id).stream()
                .map(m -> new MiembroEquipo((UUID) m.get("Id"), (String) m.get("CorreoElectronico"), (String) m.get("Nombre"),
                        (String) m.get("Rol")))
                .toList();
        Timestamp fin = (Timestamp) fila.get("FinPeriodoActual");
        return new TiendaAdminDetalle(aTienda(fila), (String) fila.get("CorreoContacto"), dinero(fila.get("Reembolsado")),
                dinero(fila.get("Comisiones")), dinero(fila.get("Saldo")), fin == null ? null : fin.toInstant(), equipo);
    }

    @Transactional(readOnly = true)
    public Page<UsuarioAdmin> usuarios(String q, String estado, int pagina) {
        String filtro = texto(estado) == null ? null : estado.strip().toUpperCase(Locale.ROOT);
        if (filtro != null && !List.of("ACTIVO", "INACTIVO", "BLOQUEADO").contains(filtro)) {
            throw new SolicitudInvalidaException("VAL-003", "El estado debe ser ACTIVO, INACTIVO o BLOQUEADO");
        }
        String patron = patron(q);
        int p = Math.max(pagina, 0);
        List<UsuarioAdmin> filas = repositorio.usuarios(patron, filtro, TAMANO_PAGINA, (long) p * TAMANO_PAGINA)
                .stream().map(AdministracionServicio::aUsuario).toList();
        return new PageImpl<>(filas, PageRequest.of(p, TAMANO_PAGINA), repositorio.contarUsuarios(patron, filtro));
    }

    @Transactional(readOnly = true)
    public UsuarioAdminDetalle usuario(UUID id) {
        Map<String, Object> fila = repositorio.usuario(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("USR-404", "No encontramos ese usuario"));
        List<TiendaDeUsuario> tiendas = repositorio.tiendasDeUsuario(id).stream()
                .map(t -> new TiendaDeUsuario((UUID) t.get("Id"), (String) t.get("Nombre"), (String) t.get("Subdominio"),
                        (String) t.get("Rol")))
                .toList();
        return new UsuarioAdminDetalle(aUsuario(fila), (String) fila.get("Telefono"), tiendas);
    }

    /** Auditoría de solo lectura. {@code categoria=SEGURIDAD} deja solo los eventos de seguridad; otro valor, el resto. */
    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> auditoria(UUID usuarioId, String entidad, String accion, String categoria,
                                             LocalDate desde, LocalDate hasta, int pagina) {
        Boolean soloSeguridad = null;
        if (texto(categoria) != null) {
            soloSeguridad = switch (categoria.strip().toUpperCase(Locale.ROOT)) {
                case "SEGURIDAD" -> Boolean.TRUE;
                case "NEGOCIO" -> Boolean.FALSE;
                default -> throw new SolicitudInvalidaException("VAL-003", "La categoría debe ser SEGURIDAD o NEGOCIO");
            };
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("VAL-003", "El inicio del rango no puede ser posterior al fin");
        }
        MapSqlParameterSource filtros = new MapSqlParameterSource()
                .addValue("usuarioId", usuarioId)
                .addValue("entidad", texto(entidad))
                .addValue("accion", texto(accion) == null ? null : accion.strip().toUpperCase(Locale.ROOT))
                .addValue("soloSeguridad", soloSeguridad)
                .addValue("desde", desde == null ? null : Timestamp.from(desde.atStartOfDay().toInstant(ZoneOffset.of("-05:00"))))
                .addValue("hasta", hasta == null ? null : Timestamp.from(hasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.of("-05:00"))));
        int p = Math.max(pagina, 0);
        List<RegistroAuditoria> filas = repositorio.auditoria(filtros, TAMANO_PAGINA, (long) p * TAMANO_PAGINA).stream()
                .map(AdministracionServicio::aRegistro).toList();
        return new PageImpl<>(filas, PageRequest.of(p, TAMANO_PAGINA), repositorio.contarAuditoria(filtros));
    }

    @Transactional(readOnly = true)
    public Page<PedidoAdmin> pedidos(String q, int pagina) {
        String patron = patron(q);
        int p = Math.max(pagina, 0);
        List<PedidoAdmin> filas = repositorio.pedidos(patron, TAMANO_PAGINA, (long) p * TAMANO_PAGINA).stream()
                .map(f -> new PedidoAdmin((UUID) f.get("Id"), (String) f.get("NumeroPedido"), (String) f.get("CorreoCliente"),
                        (String) f.get("Moneda"), dinero(f.get("Total")), (String) f.get("PasarelaPago"),
                        ((Number) f.get("Ordenes")).intValue(), ((Number) f.get("Pagadas")).intValue(),
                        instante(f.get("FechaCreacion"))))
                .toList();
        return new PageImpl<>(filas, PageRequest.of(p, TAMANO_PAGINA), repositorio.contarPedidos(patron));
    }

    // ------------------------------------------------------------------ mapeos

    private static TiendaAdmin aTienda(Map<String, Object> f) {
        return new TiendaAdmin((UUID) f.get("Id"), (String) f.get("Nombre"), (String) f.get("Subdominio"),
                Boolean.TRUE.equals(f.get("EstaActiva")), (String) f.get("Moneda"),
                new Duenio((UUID) f.get("DuenioId"), (String) f.get("DuenioCorreo"), (String) f.get("DuenioNombre")),
                (String) f.get("Plan"), (String) f.get("EstadoSuscripcion"), dinero(f.get("Ventas")),
                ((Number) f.get("Ordenes")).intValue(), instante(f.get("FechaCreacion")));
    }

    private static UsuarioAdmin aUsuario(Map<String, Object> f) {
        Instant bloqueadoHasta = instante(f.get("BloqueadoHasta"));
        return new UsuarioAdmin((UUID) f.get("Id"), (String) f.get("CorreoElectronico"), (String) f.get("Nombres"),
                (String) f.get("Apellidos"), Boolean.TRUE.equals(f.get("EstaActivo")),
                bloqueadoHasta != null && bloqueadoHasta.isAfter(Instant.now()), bloqueadoHasta,
                Boolean.TRUE.equals(f.get("DosPasosActivo")), Boolean.TRUE.equals(f.get("EsAdministradorPlataforma")),
                ((Number) f.get("TiendasPropias")).intValue(), instante(f.get("FechaCreacion")));
    }

    private static RegistroAuditoria aRegistro(Map<String, Object> f) {
        String accion = (String) f.get("Accion");
        return new RegistroAuditoria((UUID) f.get("Id"), accion.startsWith("SEGURIDAD_") ? "SEGURIDAD" : "NEGOCIO", accion,
                (String) f.get("Entidad"), (UUID) f.get("EntidadId"), (UUID) f.get("UsuarioId"), (UUID) f.get("TiendaId"),
                (String) f.get("Motivo"), detalle((String) f.get("Detalle")), instante(f.get("FechaCreacion")));
    }

    private static Map<String, Object> detalle(String json) {
        if (json == null) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return Map.of("texto", json);
        }
    }

    private static Instant instante(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Timestamp t) {
            return t.toInstant();
        }
        if (valor instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        return Instant.parse(valor.toString());
    }

    private static BigDecimal dinero(Object valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(valor.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /** Minúsculas y comodines escapados; nulo si no hay texto. */
    private static String patron(String q) {
        String limpio = texto(q);
        if (limpio == null) {
            return null;
        }
        if (limpio.length() > LARGO_MAXIMO_TEXTO) {
            throw new SolicitudInvalidaException("VAL-003", "La búsqueda no puede superar " + LARGO_MAXIMO_TEXTO + " caracteres");
        }
        String escapado = limpio.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
