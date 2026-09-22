package shopify.api.core.modules.usuario.domain.modelo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    private UUID id;
    private String correoElectronico;
    private String contrasenaHash;
    private String nombres;
    private String apellidos;
    private String telefono;
    private Boolean estaActivo;
    private Rol rol;
    private Instant fechaCreacion;
}