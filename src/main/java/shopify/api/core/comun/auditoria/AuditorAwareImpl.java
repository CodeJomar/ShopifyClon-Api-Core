package shopify.api.core.comun.auditoria;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.seguridad.SesionActual;

@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return SesionActual.usuarioIdOpcional();
    }
}
