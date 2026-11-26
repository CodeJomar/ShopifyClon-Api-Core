package shopify.api.core.modulos.tienda.servicio;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.cache.CacheTemporal;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ValidacionCamposException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.modulos.tienda.dto.request.ActualizarAparienciaRequest;
import shopify.api.core.modulos.tienda.dto.response.AparienciaResponse;
import shopify.api.core.modulos.tienda.dto.response.OpcionesAparienciaResponse;
import shopify.api.core.modulos.tienda.dto.response.OpcionesAparienciaResponse.Opciones;
import shopify.api.core.modulos.tienda.dto.response.OpcionesAparienciaResponse.PorDefecto;
import shopify.api.core.modulos.tienda.entidad.TiendaEntidad;
import shopify.api.core.modulos.tienda.mapeador.TiendaMapeador;
import shopify.api.core.modulos.tienda.modelo.OpcionesApariencia;
import shopify.api.core.modulos.tienda.modelo.ValidadorUrlLogo;
import shopify.api.core.modulos.tienda.repositorio.TiendaRepositorio;

/** Apariencia de la vitrina (R-031): cinco códigos de lista blanca y el logo (URL https). Los cambios quedan auditados. */
@Service
@RequiredArgsConstructor
public class AparienciaTiendaServicio {

    private final TiendaRepositorio tiendaRepositorio;
    private final TiendaMapeador mapeador;
    private final AuditoriaServicio auditoriaServicio;
    private final CacheTemporal cache;

    @Transactional
    public AparienciaResponse cambiar(ContextoTienda contexto, ActualizarAparienciaRequest solicitud) {
        TiendaEntidad tienda = tiendaRepositorio.findByIdAndEliminadoFalse(contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "No encontramos esa tienda"));
        AparienciaResponse anterior = mapeador.aApariencia(tienda);

        // Ausente conserva el logo; nulo o vacío lo quita; un valor debe ser una imagen que esta tienda subió a la API
        if (solicitud.logoUrlPresente()) {
            String url = solicitud.logoUrl() == null ? "" : solicitud.logoUrl().strip();
            if (url.isEmpty()) {
                tienda.setUrlLogo(null);
            } else if (ValidadorUrlLogo.esValida(url)) {
                tienda.setUrlLogo(url);
            } else {
                throw new ValidacionCamposException(Map.of("logo_url", ValidadorUrlLogo.MENSAJE));
            }
        }
        tienda.setPaleta(solicitud.paleta());
        tienda.setTipografia(solicitud.tipografia());
        tienda.setTamanoLetra(solicitud.tamanoLetra());
        tienda.setDiseno(solicitud.diseno());
        tienda.setModo(solicitud.modo());

        AparienciaResponse nueva = mapeador.aApariencia(tiendaRepositorio.saveAndFlush(tienda));
        if (!nueva.equals(anterior)) {
            auditoriaServicio.registrar(tienda.getId(), AccionAuditoria.TIENDA_APARIENCIA_CAMBIADA, "Tiendas", tienda.getId(),
                    null, Map.of("anterior", resumen(anterior), "nueva", resumen(nueva)));
            // Las lecturas públicas recordadas (vitrina, mercado) llevan la apariencia anterior
            cache.vaciar();
        }
        return nueva;
    }

    public OpcionesAparienciaResponse opciones() {
        return new OpcionesAparienciaResponse(
                new Opciones(OpcionesApariencia.PALETA, OpcionesApariencia.TIPOGRAFIA, OpcionesApariencia.TAMANO_LETRA,
                        OpcionesApariencia.DISENO, OpcionesApariencia.MODO),
                new PorDefecto(OpcionesApariencia.porDefecto(OpcionesApariencia.PALETA),
                        OpcionesApariencia.porDefecto(OpcionesApariencia.TIPOGRAFIA),
                        OpcionesApariencia.porDefecto(OpcionesApariencia.TAMANO_LETRA),
                        OpcionesApariencia.porDefecto(OpcionesApariencia.DISENO),
                        OpcionesApariencia.porDefecto(OpcionesApariencia.MODO)));
    }

    private static String resumen(AparienciaResponse a) {
        return a.paleta() + "/" + a.tipografia() + "/" + a.tamanoLetra() + "/" + a.diseno() + "/" + a.modo()
                + (a.logoUrl() == null ? "" : "/con-logo");
    }
}
