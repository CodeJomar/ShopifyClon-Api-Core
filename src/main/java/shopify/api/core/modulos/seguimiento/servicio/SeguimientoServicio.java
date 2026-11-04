package shopify.api.core.modulos.seguimiento.servicio;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.envio.modelo.EnvioSeguimiento;
import shopify.api.core.modulos.envio.servicio.EnvioServicio;
import shopify.api.core.modulos.ruta.servicio.RutaServicio;
import shopify.api.core.modulos.seguimiento.dto.response.SeguimientoPublicoResponse;
import shopify.api.core.modulos.seguimiento.dto.response.SeguimientoPublicoResponse.Evento;

/** Seguimiento público por número de guía: junta el envío y su chofer sin exponer datos personales. */
@Service
@RequiredArgsConstructor
public class SeguimientoServicio {

    private final EnvioServicio envios;
    private final RutaServicio rutas;

    /** Un 404 genérico: no se distingue una guía inexistente de la de otra tienda. */
    public SeguimientoPublicoResponse porNumeroGuia(String numeroGuia) {
        EnvioSeguimiento envio = envios.seguimiento(numeroGuia)
                .orElseThrow(() -> new RecursoNoEncontradoException("ENV-404", "No encontramos un envío con esa guía"));
        return new SeguimientoPublicoResponse(envio.numeroGuia(), envio.estado(), envio.empresaTransporte(),
                rutas.choferDe(envio.envioId()).orElse(null), envio.fechaDespacho(), envio.fechaEntregaEstimada(),
                envio.fechaEntregaReal(),
                envio.eventos().stream()
                        .map(e -> new Evento(e.id(), e.estado().name(), e.nota(), e.fotoUrl(), e.fechaEvento()))
                        .toList());
    }
}
