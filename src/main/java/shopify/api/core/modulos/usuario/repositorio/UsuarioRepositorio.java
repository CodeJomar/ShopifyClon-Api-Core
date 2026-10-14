package shopify.api.core.modulos.usuario.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.modelo.TiendaDeUsuarioVista;

public interface UsuarioRepositorio extends JpaRepository<UsuarioEntidad, UUID> {

    /** Busca por correo ya normalizado a minúsculas, sin contar las cuentas eliminadas. */
    @Query("SELECT u FROM UsuarioEntidad u WHERE LOWER(u.correoElectronico) = :correo AND u.eliminado = FALSE")
    Optional<UsuarioEntidad> buscarVigentePorCorreo(@Param("correo") String correo);

    /** Incluye cuentas eliminadas: el correo sigue ocupado (USR-001). */
    @Query("SELECT COUNT(u) > 0 FROM UsuarioEntidad u WHERE LOWER(u.correoElectronico) = :correo")
    boolean existePorCorreo(@Param("correo") String correo);

    @Query(value = """
            SELECT t."Id" AS "tiendaId", t."Nombre" AS "nombre", t."Subdominio" AS "subdominio",
                   CASE WHEN t."PropietarioId" = :usuarioId THEN 'PROPIETARIO' ELSE r."Codigo" END AS "rol"
            FROM "Tiendas" t
            LEFT JOIN "PersonalTienda" p ON p."TiendaId" = t."Id" AND p."UsuarioId" = :usuarioId AND p."Eliminado" = FALSE
            LEFT JOIN "Roles" r ON r."Id" = p."RolId" AND r."Eliminado" = FALSE
            WHERE t."Eliminado" = FALSE
              AND (t."PropietarioId" = :usuarioId OR r."Codigo" IN ('ADMINISTRADOR', 'GESTOR_CATALOGO', 'GESTOR_PEDIDOS'))
            ORDER BY t."Nombre"
            """, nativeQuery = true)
    List<TiendaDeUsuarioVista> listarTiendasDeUsuario(@Param("usuarioId") UUID usuarioId);
}
