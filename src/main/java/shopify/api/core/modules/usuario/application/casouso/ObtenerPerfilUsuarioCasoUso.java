package shopify.api.core.modules.usuario.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.usuario.application.dto.response.UsuarioResponse;
import shopify.api.core.modules.usuario.application.mapeador.UsuarioAplicacionMapeador;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;
import shopify.api.core.modules.usuario.domain.puerto.salida.UsuarioRepositorioPuerto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;
import shopify.api.core.shared.seguridad.servicio.SeguridadUtil;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObtenerPerfilUsuarioCasoUso {

    private final UsuarioRepositorioPuerto usuarioRepositorio;
    private final UsuarioAplicacionMapeador mapeador;

    @Transactional(readOnly = true)
    public UsuarioResponse ejecutar() {
        UUID usuarioId = SeguridadUtil.obtenerUsuarioIdActual();

        Usuario usuario = usuarioRepositorio.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + usuarioId));

        return mapeador.aResponse(usuario);
    }
}