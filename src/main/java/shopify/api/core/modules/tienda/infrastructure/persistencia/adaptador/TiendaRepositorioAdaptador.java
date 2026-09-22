package shopify.api.core.modules.tienda.infrastructure.persistencia.adaptador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import shopify.api.core.modules.tienda.domain.modelo.Tienda;
import shopify.api.core.modules.tienda.domain.puerto.salida.TiendaRepositorioPuerto;
import shopify.api.core.modules.tienda.infrastructure.persistencia.TiendaPersistenciaMapeador;
import shopify.api.core.modules.tienda.infrastructure.persistencia.entidad.TiendaEntidad;
import shopify.api.core.modules.tienda.infrastructure.persistencia.repositorio.TiendaJpaRepositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TiendaRepositorioAdaptador implements TiendaRepositorioPuerto {

    private final TiendaJpaRepositorio jpaRepositorio;
    private final TiendaPersistenciaMapeador mapeador;

    @Override
    public Tienda guardar(Tienda tienda) {
        TiendaEntidad entidad = mapeador.aEntidad(tienda);
        TiendaEntidad guardada = jpaRepositorio.save(entidad);
        return mapeador.aDominio(guardada);
    }

    @Override
    public Optional<Tienda> buscarPorId(UUID id) {
        return jpaRepositorio.findByIdAndEliminadoFalse(id)
                .map(mapeador::aDominio);
    }

    @Override
    public Optional<Tienda> buscarPorSubdominio(String subdominio) {
        return jpaRepositorio.findBySubdominioIgnoreCaseAndEliminadoFalse(subdominio)
                .map(mapeador::aDominio);
    }

    @Override
    public List<Tienda> buscarPorPropietarioId(UUID propietarioId) {
        return jpaRepositorio.findByPropietarioIdAndEliminadoFalse(propietarioId).stream()
                .map(mapeador::aDominio)
                .toList();
    }

    @Override
    public boolean existePorSubdominio(String subdominio) {
        return jpaRepositorio.existsBySubdominioIgnoreCaseAndEliminadoFalse(subdominio);
    }
}