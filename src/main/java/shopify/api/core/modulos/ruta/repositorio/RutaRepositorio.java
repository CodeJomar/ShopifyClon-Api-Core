package shopify.api.core.modulos.ruta.repositorio;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.ruta.entidad.RutaEntidad;
import shopify.api.core.modulos.ruta.modelo.ConteoRutaVista;
import shopify.api.core.modulos.ruta.modelo.EnvioDisponibleVista;
import shopify.api.core.modulos.ruta.modelo.EnvioParaRutaVista;
import shopify.api.core.modulos.ruta.modelo.EstadoRuta;
import shopify.api.core.modulos.ruta.modelo.ItemParadaVista;
import shopify.api.core.modulos.ruta.modelo.ParadaVista;
import shopify.api.core.modulos.ruta.modelo.RemitenteVista;

/**
 * Rutas y sus paradas. Las consultas nativas leen {@code Envios}, {@code Ordenes}, {@code ItemsOrden} y
 * {@code Tiendas} solo para mostrar (sin modificarlas), como permite {@code arquitectura-backend.md}.
 */
public interface RutaRepositorio extends JpaRepository<RutaEntidad, UUID> {

    Optional<RutaEntidad> findByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    /** Bloquea la ruta para cambiar su estado (iniciar, finalizar, editar). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RutaEntidad r WHERE r.id = :id AND r.tiendaId = :tiendaId AND r.eliminado = FALSE")
    Optional<RutaEntidad> bloquearPorIdYTienda(@Param("id") UUID id, @Param("tiendaId") UUID tiendaId);

    @Query("""
            SELECT r FROM RutaEntidad r
            WHERE r.tiendaId = :tiendaId AND r.eliminado = FALSE AND r.estado IN :estados
              AND r.fecha BETWEEN :desde AND :hasta
            """)
    Page<RutaEntidad> buscar(@Param("tiendaId") UUID tiendaId, @Param("estados") Collection<EstadoRuta> estados,
                             @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, Pageable pagina);

    /** Serializa la creación de rutas de una tienda (código correlativo y asignación de envíos). */
    @Query(value = "SELECT t.\"Id\" FROM \"Tiendas\" t WHERE t.\"Id\" = :tiendaId FOR UPDATE", nativeQuery = true)
    Optional<UUID> bloquearTienda(@Param("tiendaId") UUID tiendaId);

    @Query(value = "SELECT COUNT(*) FROM \"Rutas\" WHERE \"TiendaId\" = :tiendaId", nativeQuery = true)
    long contarDeTienda(@Param("tiendaId") UUID tiendaId);

    @Query(value = "SELECT t.\"ZonaHoraria\" FROM \"Tiendas\" t WHERE t.\"Id\" = :tiendaId", nativeQuery = true)
    Optional<String> zonaHorariaDe(@Param("tiendaId") UUID tiendaId);

    @Query(value = """
            SELECT t."Nombre" AS "nombre", t."CorreoContacto" AS "correo"
            FROM "Tiendas" t WHERE t."Id" = :tiendaId
            """, nativeQuery = true)
    Optional<RemitenteVista> remitenteDe(@Param("tiendaId") UUID tiendaId);

    @Query(value = """
            SELECT e."Id" AS "envioId", e."Estado" AS "estado", o."TiendaId" AS "tiendaId"
            FROM "Envios" e JOIN "Ordenes" o ON o."Id" = e."OrdenId"
            WHERE e."Id" IN (:ids) AND e."Eliminado" = FALSE
            """, nativeQuery = true)
    List<EnvioParaRutaVista> enviosPorIds(@Param("ids") Collection<UUID> ids);

    /** RUT-002: envíos que ya están en otra ruta que no está finalizada. */
    @Query(value = """
            SELECT DISTINCT p."EnvioId"
            FROM "ParadasRuta" p JOIN "Rutas" r ON r."Id" = p."RutaId"
            WHERE p."EnvioId" IN (:ids) AND p."Eliminado" = FALSE AND r."Eliminado" = FALSE
              AND r."Estado" <> 'FINALIZADA' AND r."Id" <> :excluirRutaId
            """, nativeQuery = true)
    List<UUID> enviosEnRutaActiva(@Param("ids") Collection<UUID> ids, @Param("excluirRutaId") UUID excluirRutaId);

    @Query(value = """
            SELECT p."Posicion" AS "posicion", e."Id" AS "envioId", o."Id" AS "ordenId", o."NumeroOrden" AS "numeroOrden",
                   e."NumeroGuia" AS "numeroGuia", e."Estado" AS "estadoEnvio", o."NombreCliente" AS "nombreCliente",
                   o."TelefonoCliente" AS "telefonoCliente", CAST(o."DireccionEnvio" AS TEXT) AS "direccion"
            FROM "ParadasRuta" p
            JOIN "Envios" e ON e."Id" = p."EnvioId"
            JOIN "Ordenes" o ON o."Id" = e."OrdenId"
            WHERE p."RutaId" = :rutaId AND p."Eliminado" = FALSE
            ORDER BY p."Posicion"
            """, nativeQuery = true)
    List<ParadaVista> paradasDe(@Param("rutaId") UUID rutaId);

    @Query(value = """
            SELECT i."OrdenId" AS "ordenId", i."TituloProducto" AS "tituloProducto", i."TituloVariante" AS "tituloVariante",
                   i."SKU" AS "sku", i."Cantidad" AS "cantidad"
            FROM "ItemsOrden" i
            WHERE i."OrdenId" IN (:ordenIds) AND i."Eliminado" = FALSE
            ORDER BY i."FechaCreacion"
            """, nativeQuery = true)
    List<ItemParadaVista> itemsDe(@Param("ordenIds") Collection<UUID> ordenIds);

    @Query(value = """
            SELECT p."RutaId" AS "rutaId", COUNT(*) AS "total",
                   COUNT(*) FILTER (WHERE e."Estado" = 'ENTREGADO') AS "entregadas"
            FROM "ParadasRuta" p JOIN "Envios" e ON e."Id" = p."EnvioId"
            WHERE p."RutaId" IN (:rutaIds) AND p."Eliminado" = FALSE
            GROUP BY p."RutaId"
            """, nativeQuery = true)
    List<ConteoRutaVista> conteosDe(@Param("rutaIds") Collection<UUID> rutaIds);

    /** Chofer de la ruta más reciente en la que va el envío (seguimiento público). */
    @Query(value = """
            SELECT r."Chofer"
            FROM "ParadasRuta" p JOIN "Rutas" r ON r."Id" = p."RutaId"
            WHERE p."EnvioId" = :envioId AND p."Eliminado" = FALSE AND r."Eliminado" = FALSE
            ORDER BY r."FechaCreacion" DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> choferDe(@Param("envioId") UUID envioId);

    @Query(value = """
            SELECT e."Id" AS "envioId", o."NumeroOrden" AS "numeroOrden", e."NumeroGuia" AS "numeroGuia",
                   e."Estado" AS "estadoEnvio",
                   COALESCE(o."DireccionEnvio"->>'nombre_contacto', o."NombreCliente") AS "destinatario",
                   o."DireccionEnvio"->>'ciudad' AS "ciudad",
                   o."DireccionEnvio"->>'direccion_linea1' AS "direccionLinea1",
                   (SELECT COALESCE(SUM(i."Cantidad"), 0) FROM "ItemsOrden" i
                     WHERE i."OrdenId" = o."Id" AND i."Eliminado" = FALSE) AS "totalItems"
            FROM "Envios" e JOIN "Ordenes" o ON o."Id" = e."OrdenId"
            WHERE o."TiendaId" = :tiendaId AND e."Eliminado" = FALSE AND e."Estado" IN ('CREADO', 'EN_TRANSITO')
              AND NOT EXISTS (
                SELECT 1 FROM "ParadasRuta" p JOIN "Rutas" r ON r."Id" = p."RutaId"
                WHERE p."EnvioId" = e."Id" AND p."Eliminado" = FALSE AND r."Eliminado" = FALSE
                  AND r."Estado" <> 'FINALIZADA')
            ORDER BY e."FechaCreacion"
            """, nativeQuery = true)
    List<EnvioDisponibleVista> disponiblesParaRuta(@Param("tiendaId") UUID tiendaId);
}
