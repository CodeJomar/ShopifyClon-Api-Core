package shopify.api.core.modulos.tienda.repositorio;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.tienda.entidad.TiendaEntidad;
import shopify.api.core.modulos.tienda.modelo.MiTiendaVista;

public interface TiendaRepositorio extends JpaRepository<TiendaEntidad, UUID> {

    /** Cuenta también las tiendas eliminadas: el subdominio no se reutiliza (TIE-001). */
    boolean existsBySubdominio(String subdominio);

    Optional<TiendaEntidad> findByIdAndEliminadoFalse(UUID id);

    /** Serializa los cambios de personal de una misma tienda (límite del plan, PER-002). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TiendaEntidad t WHERE t.id = :id AND t.eliminado = FALSE")
    Optional<TiendaEntidad> bloquearPorId(@Param("id") UUID id);

    Optional<TiendaEntidad> findBySubdominioAndEliminadoFalse(String subdominio);

    /** TIE-005: con alguna orden la moneda ya no se puede cambiar. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM \"Ordenes\" WHERE \"TiendaId\" = :tiendaId)", nativeQuery = true)
    boolean tieneOrdenes(@Param("tiendaId") UUID tiendaId);

    @Query(value = """
            SELECT t."Id" AS "tiendaId", t."Nombre" AS "nombre", t."Subdominio" AS "subdominio",
                   CASE WHEN t."PropietarioId" = :usuarioId THEN 'PROPIETARIO' ELSE r."Codigo" END AS "rol",
                   t."EstaActiva" AS "estaActiva"
            FROM "Tiendas" t
            LEFT JOIN "PersonalTienda" p ON p."TiendaId" = t."Id" AND p."UsuarioId" = :usuarioId AND p."Eliminado" = FALSE
            LEFT JOIN "Roles" r ON r."Id" = p."RolId" AND r."Eliminado" = FALSE
            WHERE t."Eliminado" = FALSE
              AND (t."PropietarioId" = :usuarioId OR r."Codigo" IN ('ADMINISTRADOR', 'GESTOR_CATALOGO', 'GESTOR_PEDIDOS'))
            ORDER BY t."Nombre"
            """, nativeQuery = true)
    List<MiTiendaVista> listarDeUsuario(@Param("usuarioId") UUID usuarioId);
}
