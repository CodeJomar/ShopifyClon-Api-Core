package shopify.api.core.comun.auditoria;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepositorio extends JpaRepository<RegistroAuditoriaEntidad, UUID> {
}
