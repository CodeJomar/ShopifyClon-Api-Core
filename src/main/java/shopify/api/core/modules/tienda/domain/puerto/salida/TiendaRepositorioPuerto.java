package shopify.api.core.modules.tienda.domain.puerto.salida;

import shopify.api.core.modules.tienda.domain.modelo.Tienda;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TiendaRepositorioPuerto {
    Tienda guardar(Tienda tienda);
    Optional<Tienda> buscarPorId(UUID id);
    Optional<Tienda> buscarPorSubdominio(String subdominio);
    List<Tienda> buscarPorPropietarioId(UUID propietarioId);
    boolean existePorSubdominio(String subdominio);
}