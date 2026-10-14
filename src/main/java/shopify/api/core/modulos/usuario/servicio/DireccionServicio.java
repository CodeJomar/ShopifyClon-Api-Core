package shopify.api.core.modulos.usuario.servicio;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.usuario.dto.request.DireccionRequest;
import shopify.api.core.modulos.usuario.dto.response.DireccionResponse;
import shopify.api.core.modulos.usuario.entidad.DireccionUsuarioEntidad;
import shopify.api.core.modulos.usuario.mapeador.DireccionMapeador;
import shopify.api.core.modulos.usuario.repositorio.DireccionUsuarioRepositorio;

/** Libreta de direcciones del usuario en sesión (USR-008, USR-011). */
@Service
@RequiredArgsConstructor
public class DireccionServicio {

    static final int MAX_DIRECCIONES = 20;

    private final DireccionUsuarioRepositorio repositorio;
    private final DireccionMapeador mapeador;

    @Transactional(readOnly = true)
    public List<DireccionResponse> listar() {
        return mapeador.aResponses(repositorio
                .findByUsuarioIdAndEliminadoFalseOrderByEsPrincipalDescFechaCreacionDesc(SesionActual.usuarioId()));
    }

    /** La primera dirección es principal; si se pide principal, desmarca la anterior (USR-008). */
    @Transactional
    public DireccionResponse crear(DireccionRequest request) {
        UUID usuarioId = SesionActual.usuarioId();
        long existentes = repositorio.countByUsuarioIdAndEliminadoFalse(usuarioId);
        if (existentes >= MAX_DIRECCIONES) {
            throw new ReglaNegocioException("USR-011", "Solo puedes guardar hasta " + MAX_DIRECCIONES + " direcciones");
        }

        boolean seraPrincipal = existentes == 0 || Boolean.TRUE.equals(request.esPrincipal());
        if (seraPrincipal) {
            repositorio.desmarcarPrincipales(usuarioId);
        }

        DireccionUsuarioEntidad direccion = mapeador.aEntidad(request);
        direccion.setUsuarioId(usuarioId);
        direccion.setEsPrincipal(seraPrincipal);
        return mapeador.aResponse(repositorio.save(direccion));
    }

    @Transactional
    public DireccionResponse actualizar(UUID id, DireccionRequest request) {
        DireccionUsuarioEntidad direccion = buscarPropia(id);
        mapeador.actualizar(request, direccion);
        return mapeador.aResponse(repositorio.save(direccion));
    }

    /** Eliminación lógica; si era la principal, la más reciente de las restantes pasa a serlo (USR-008). */
    @Transactional
    public void eliminar(UUID id) {
        UUID usuarioId = SesionActual.usuarioId();
        DireccionUsuarioEntidad direccion = buscarPropia(id);
        boolean eraPrincipal = Boolean.TRUE.equals(direccion.getEsPrincipal());

        direccion.setEliminado(true);
        direccion.setEsPrincipal(false);
        repositorio.saveAndFlush(direccion);

        if (eraPrincipal) {
            repositorio.findFirstByUsuarioIdAndEliminadoFalseOrderByFechaCreacionDesc(usuarioId)
                    .ifPresent(siguiente -> {
                        siguiente.setEsPrincipal(true);
                        repositorio.save(siguiente);
                    });
        }
    }

    @Transactional
    public DireccionResponse marcarPrincipal(UUID id) {
        UUID usuarioId = SesionActual.usuarioId();
        buscarPropia(id);
        repositorio.desmarcarPrincipales(usuarioId);

        DireccionUsuarioEntidad direccion = buscarPropia(id);
        direccion.setEsPrincipal(true);
        return mapeador.aResponse(repositorio.save(direccion));
    }

    private DireccionUsuarioEntidad buscarPropia(UUID id) {
        return repositorio.findByIdAndUsuarioIdAndEliminadoFalse(id, SesionActual.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("USR-404", "No encontramos esa dirección"));
    }
}
