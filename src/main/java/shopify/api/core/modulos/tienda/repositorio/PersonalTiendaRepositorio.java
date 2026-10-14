package shopify.api.core.modulos.tienda.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.tienda.entidad.PersonalTiendaEntidad;
import shopify.api.core.modulos.tienda.modelo.MiembroPersonalVista;
import shopify.api.core.modulos.tienda.modelo.UsuarioInvitadoVista;

public interface PersonalTiendaRepositorio extends JpaRepository<PersonalTiendaEntidad, UUID> {

    Optional<PersonalTiendaEntidad> findByTiendaIdAndUsuarioIdAndEliminadoFalse(UUID tiendaId, UUID usuarioId);

    boolean existsByTiendaIdAndUsuarioIdAndEliminadoFalse(UUID tiendaId, UUID usuarioId);

    /** Personal activo sin contar al propietario (PER-002). */
    @Query(value = """
            SELECT COUNT(*) FROM "PersonalTienda" p
            JOIN "Tiendas" t ON t."Id" = p."TiendaId"
            WHERE p."TiendaId" = :tiendaId AND p."Eliminado" = FALSE AND p."UsuarioId" <> t."PropietarioId"
            """, nativeQuery = true)
    long contarSinPropietario(@Param("tiendaId") UUID tiendaId);

    /** Solo lectura sobre {@code Usuarios}: no depende del módulo usuario. */
    @Query(value = """
            SELECT u."Id" AS "id", u."CorreoElectronico" AS "correoElectronico",
                   u."Nombres" AS "nombres", u."Apellidos" AS "apellidos"
            FROM "Usuarios" u
            WHERE LOWER(u."CorreoElectronico") = :correo AND u."EstaActivo" = TRUE AND u."Eliminado" = FALSE
            """, nativeQuery = true)
    Optional<UsuarioInvitadoVista> buscarUsuarioActivoPorCorreo(@Param("correo") String correo);

    @Query(value = """
            SELECT u."Id" AS "usuarioId", u."CorreoElectronico" AS "correoElectronico", u."Nombres" AS "nombres",
                   u."Apellidos" AS "apellidos", r."Codigo" AS "rol", p."FechaCreacion" AS "desde"
            FROM "PersonalTienda" p
            JOIN "Usuarios" u ON u."Id" = p."UsuarioId"
            JOIN "Roles" r ON r."Id" = p."RolId"
            WHERE p."TiendaId" = :tiendaId AND p."Eliminado" = FALSE
            ORDER BY CASE WHEN r."Codigo" = 'PROPIETARIO' THEN 0 ELSE 1 END, p."FechaCreacion"
            """, nativeQuery = true)
    List<MiembroPersonalVista> listarMiembros(@Param("tiendaId") UUID tiendaId);

    @Query(value = """
            SELECT u."Id" AS "usuarioId", u."CorreoElectronico" AS "correoElectronico", u."Nombres" AS "nombres",
                   u."Apellidos" AS "apellidos", r."Codigo" AS "rol", p."FechaCreacion" AS "desde"
            FROM "PersonalTienda" p
            JOIN "Usuarios" u ON u."Id" = p."UsuarioId"
            JOIN "Roles" r ON r."Id" = p."RolId"
            WHERE p."TiendaId" = :tiendaId AND p."UsuarioId" = :usuarioId AND p."Eliminado" = FALSE
            """, nativeQuery = true)
    Optional<MiembroPersonalVista> buscarMiembro(@Param("tiendaId") UUID tiendaId, @Param("usuarioId") UUID usuarioId);
}
