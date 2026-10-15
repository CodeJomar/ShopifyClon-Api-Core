package shopify.api.core.modulos.envio.servicio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.envio.modelo.DatosEnvio;
import shopify.api.core.modulos.envio.repositorio.EnvioRepositorio;

/** Lectura de un envío para los correos de notificación. Solo lectura. */
@Service
@RequiredArgsConstructor
public class EnvioDatosServicio {

    private final EnvioRepositorio envios;

    @Transactional(readOnly = true)
    public DatosEnvio datos(UUID envioId) {
        var envio = envios.findFirstByIdAndEliminadoFalse(envioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("ENV-404", "No encontramos ese envío"));
        return new DatosEnvio(envio.getOrdenId(), envio.getEmpresaTransporte(), envio.getNumeroGuia(),
                envio.getUrlSeguimiento());
    }
}
