package shopify.api.core.modules.usuario.domain.puerto.salida;

import shopify.api.core.modules.usuario.domain.modelo.Rol;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositorioPuerto {
    Usuario guardar(Usuario usuario);
    Optional<Usuario> buscarPorId(UUID id);
    Optional<Usuario> buscarPorCorreo(String correo);
    boolean existePorCorreo(String correo);
    Optional<Rol> buscarRolPorCodigo(String codigo);
    Rol guardarRol(Rol rol);
}