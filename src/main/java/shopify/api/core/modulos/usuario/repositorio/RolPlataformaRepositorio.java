package shopify.api.core.modulos.usuario.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.usuario.entidad.RolPlataformaEntidad;

public interface RolPlataformaRepositorio extends JpaRepository<RolPlataformaEntidad, UUID> {

    List<RolPlataformaEntidad> findByEliminadoFalseOrderByNombreAsc();

    Optional<RolPlataformaEntidad> findByIdAndEliminadoFalse(UUID id);

    @Query("SELECT COUNT(r) > 0 FROM RolPlataformaEntidad r WHERE UPPER(r.codigo) = UPPER(:codigo) AND r.eliminado = FALSE")
    boolean existeCodigo(@Param("codigo") String codigo);

    @Query("SELECT COUNT(u) FROM UsuarioEntidad u WHERE u.rolPlataformaId = :rolId AND u.eliminado = FALSE")
    long contarUsuariosConRol(@Param("rolId") UUID rolId);

    /**
     * Personas que hoy pueden administrar administradores: superadministradores y quienes tienen un rol con ese
     * permiso, activas y sin eliminar. Para no dejar a la plataforma sin nadie que gestione administradores.
     */
    @Query(value = """
            SELECT COUNT(*) FROM "Usuarios" u
            WHERE u."Eliminado" = FALSE AND u."EstaActivo" = TRUE
              AND (u."EsAdministradorPlataforma" = TRUE
                   OR EXISTS (SELECT 1 FROM "RolesPlataformaPermisos" rp
                              JOIN "RolesPlataforma" r ON r."Id" = rp."RolId" AND r."Eliminado" = FALSE
                              WHERE rp."RolId" = u."RolPlataformaId" AND rp."Permiso" = 'PLATAFORMA_ADMINISTRADORES'))
            """, nativeQuery = true)
    long contarTitularesDeAdministradores();

    /** Administradores con rol, con su rol, para el listado. */
    @Query("SELECT u FROM UsuarioEntidad u WHERE u.eliminado = FALSE AND (u.esAdministradorPlataforma = TRUE "
            + "OR u.rolPlataformaId IS NOT NULL) ORDER BY u.correoElectronico")
    List<shopify.api.core.modulos.usuario.entidad.UsuarioEntidad> listarAdministradores();
}
