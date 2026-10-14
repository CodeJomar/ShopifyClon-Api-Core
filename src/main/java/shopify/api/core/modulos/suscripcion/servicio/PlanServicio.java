package shopify.api.core.modulos.suscripcion.servicio;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.suscripcion.dto.response.PlanResponse;
import shopify.api.core.modulos.suscripcion.mapeador.PlanMapeador;
import shopify.api.core.modulos.suscripcion.repositorio.PlanSuscripcionRepositorio;

/** Catálogo público de planes (SUS-013, SUS-015). */
@Service
@RequiredArgsConstructor
public class PlanServicio {

    private final PlanSuscripcionRepositorio repositorio;
    private final PlanMapeador mapeador;

    @Transactional(readOnly = true)
    public List<PlanResponse> listarPublicos() {
        return mapeador.aResponses(repositorio.findByEsPublicoTrueAndEliminadoFalseOrderByOrdenAsc());
    }
}
