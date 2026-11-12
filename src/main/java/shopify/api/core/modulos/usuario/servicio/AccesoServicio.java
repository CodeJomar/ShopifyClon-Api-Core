package shopify.api.core.modulos.usuario.servicio;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.RolTienda;
import shopify.api.core.modulos.usuario.dto.response.AccesoResponse;
import shopify.api.core.modulos.usuario.dto.response.AccesoResponse.TiendaAccesoResponse;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.modelo.TiendaDeUsuarioVista;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

/** R-001: arma el bloque {@code acceso} del perfil a partir de las tiendas de la persona y su bandera de administrador. */
@Service
@RequiredArgsConstructor
public class AccesoServicio {

    private final UsuarioRepositorio usuarioRepositorio;

    @Transactional(readOnly = true)
    public AccesoResponse de(UsuarioEntidad usuario) {
        List<TiendaAccesoResponse> tiendas = usuarioRepositorio.listarTiendasDeUsuario(usuario.getId()).stream()
                .map(AccesoServicio::aTienda)
                .toList();
        boolean esPropietario = tiendas.stream().anyMatch(TiendaAccesoResponse::esPropietarioDeLaTienda);
        boolean admin = Boolean.TRUE.equals(usuario.getEsAdministradorPlataforma());
        return new AccesoResponse(
                true,
                esPropietario,
                admin,
                admin ? PermisoPlataforma.nombresDeTodos() : List.of(),
                tiendas);
    }

    private static TiendaAccesoResponse aTienda(TiendaDeUsuarioVista vista) {
        var rol = RolTienda.desdeCodigo(vista.getRol());
        List<String> permisos = rol
                .map(r -> r.permisos().stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList())
                .orElse(List.of());
        boolean propietario = rol.map(r -> r == RolTienda.PROPIETARIO).orElse(false);
        return new TiendaAccesoResponse(vista.getTiendaId(), vista.getNombre(), vista.getSubdominio(), vista.getRol(),
                propietario, permisos);
    }
}
