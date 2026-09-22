package shopify.api.core.modules.usuario.infrastructure.persistencia.adaptador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import shopify.api.core.modules.usuario.domain.modelo.Rol;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;
import shopify.api.core.modules.usuario.domain.puerto.salida.UsuarioRepositorioPuerto;
import shopify.api.core.modules.usuario.infrastructure.persistencia.UsuarioPersistenciaMapeador;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.RolEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.RolJpaRepositorio;
import shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio.UsuarioJpaRepositorio;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UsuarioRepositorioAdaptador implements UsuarioRepositorioPuerto {

    private final UsuarioJpaRepositorio usuarioJpaRepositorio;
    private final RolJpaRepositorio rolJpaRepositorio;
    private final UsuarioPersistenciaMapeador mapeador;

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntidad entidad = mapeador.aEntidad(usuario);
        UsuarioEntidad guardada = usuarioJpaRepositorio.save(entidad);
        return mapeador.aDominio(guardada);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return usuarioJpaRepositorio.findById(id)
                .filter(u -> !u.getEliminado())
                .map(mapeador::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return usuarioJpaRepositorio.findByCorreoElectronicoAndEliminadoFalse(correo)
                .map(mapeador::aDominio);
    }

    @Override
    public boolean existePorCorreo(String correo) {
        return usuarioJpaRepositorio.existsByCorreoElectronicoAndEliminadoFalse(correo);
    }

    @Override
    public Optional<Rol> buscarRolPorCodigo(String codigo) {
        return rolJpaRepositorio.findByCodigoAndEliminadoFalse(codigo)
                .map(mapeador::aDominioRol);
    }

    @Override
    public Rol guardarRol(Rol rol) {
        RolEntidad entidad = mapeador.aEntidadRol(rol);
        return mapeador.aDominioRol(rolJpaRepositorio.save(entidad));
    }
}