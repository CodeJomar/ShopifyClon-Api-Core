package shopify.api.core.modules.usuario.domain.modelo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rol {
    private UUID id;
    private String nombre;
    private String codigo; // ej: "ADMIN_TIENDA", "CLIENTE"
    private String descripcion;
}