package shopify.api.core.modulos.semilla.servicio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Corre la semilla de demostración (R-014) al arrancar, después de Flyway y con el contexto listo, solo si
 * {@code SEMILLA_DEMO=true}. Sin {@code SEMILLA_DEMO_CONTRASENA} no hace nada y avisa. Un fallo se registra y no
 * impide que la API arranque.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.semilla.habilitada", havingValue = "true")
public class SemillaDemoEjecutor implements ApplicationRunner {

    private final SemillaDemoServicio semilla;

    @Value("${app.semilla.contrasena:}")
    private String contrasena;

    @Value("${app.semilla.admin-correo:admin.demo@otemu.test}")
    private String correoAdmin;

    @Value("${app.semilla.propietario-correo:propietario.demo@otemu.test}")
    private String correoPropietario;

    @Value("${app.semilla.comprador-correo:comprador.demo@otemu.test}")
    private String correoComprador;

    @Override
    public void run(ApplicationArguments argumentos) {
        if (contrasena == null || contrasena.isBlank()) {
            log.warn("SEMILLA_DEMO está activa pero falta SEMILLA_DEMO_CONTRASENA: la semilla se omite");
            return;
        }
        try {
            semilla.ejecutar(contrasena, correoAdmin, correoPropietario, correoComprador);
        } catch (RuntimeException ex) {
            log.error("La semilla de demostración falló ({}); la API sigue arrancando. Se puede reintentar: es idempotente",
                    ex.getClass().getSimpleName());
        }
    }
}
