package shopify.api.core.modulos.tienda.servicio;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.RolTienda;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;
import shopify.api.core.modulos.tienda.dto.request.AgregarPersonalRequest;
import shopify.api.core.modulos.tienda.dto.request.CambiarRolRequest;
import shopify.api.core.modulos.tienda.dto.request.QuitarPersonalRequest;
import shopify.api.core.modulos.tienda.dto.response.MiembroPersonalResponse;
import shopify.api.core.modulos.tienda.entidad.PersonalTiendaEntidad;
import shopify.api.core.modulos.tienda.entidad.RolEntidad;
import shopify.api.core.modulos.tienda.mapeador.PersonalMapeador;
import shopify.api.core.modulos.tienda.modelo.MiembroPersonalVista;
import shopify.api.core.modulos.tienda.modelo.UsuarioInvitadoVista;
import shopify.api.core.modulos.tienda.repositorio.PersonalTiendaRepositorio;
import shopify.api.core.modulos.tienda.repositorio.RolRepositorio;
import shopify.api.core.modulos.tienda.repositorio.TiendaRepositorio;

/** Personal de la tienda: PER-001 a PER-007. */
@Service
@RequiredArgsConstructor
public class PersonalServicio {

    private static final String ENTIDAD = "PersonalTienda";

    private final PersonalTiendaRepositorio personalRepositorio;
    private final TiendaRepositorio tiendaRepositorio;
    private final RolRepositorio rolRepositorio;
    private final SuscripcionServicio suscripcionServicio;
    private final AuditoriaServicio auditoriaServicio;
    private final PersonalMapeador mapeador;

    @Transactional(readOnly = true)
    public java.util.List<MiembroPersonalResponse> listar(ContextoTienda contexto) {
        return mapeador.aResponses(personalRepositorio.listarMiembros(contexto.tiendaId()));
    }

    /** Orden de validación: PER-003 (rol), PER-001, PER-006, PER-004, PER-002. */
    @Transactional
    public MiembroPersonalResponse agregar(ContextoTienda contexto, AgregarPersonalRequest request) {
        RolTienda rolNuevo = rolSolicitado(request.rol());

        UsuarioInvitadoVista invitado = personalRepositorio
                .buscarUsuarioActivoPorCorreo(request.correoElectronico().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ReglaNegocioException("PER-001",
                        "No hay una cuenta activa con ese correo. Pídele que se registre primero"));

        // Se bloquea la tienda para que dos altas simultáneas no superen el límite del plan (PER-002)
        var tienda = tiendaRepositorio.bloquearPorId(contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SEG-001", "No encontramos esa tienda"));

        if (invitado.getId().equals(tienda.getPropietarioId())
                || personalRepositorio.existsByTiendaIdAndUsuarioIdAndEliminadoFalse(tienda.getId(), invitado.getId())) {
            throw yaEsPersonal();
        }
        exigirPuedeGestionar(contexto, rolNuevo);
        exigirCupo(tienda.getId());

        PersonalTiendaEntidad miembro = new PersonalTiendaEntidad();
        miembro.setTiendaId(tienda.getId());
        miembro.setUsuarioId(invitado.getId());
        miembro.setRolId(rol(rolNuevo).getId());
        try {
            personalRepositorio.saveAndFlush(miembro);
        } catch (DataIntegrityViolationException ex) {
            // Carrera: UxPersonalTiendaUsuario
            throw yaEsPersonal();
        }

        auditoriaServicio.registrar(tienda.getId(), AccionAuditoria.PERSONAL_AGREGADO, ENTIDAD, invitado.getId(), null,
                Map.of("correo", invitado.getCorreoElectronico(), "rol", rolNuevo.name()));
        return miembro(tienda.getId(), invitado.getId());
    }

    /** Orden de validación: PER-404, PER-007, PER-003, PER-004. Mismo rol: 200 sin cambios ni auditoría. */
    @Transactional
    public MiembroPersonalResponse cambiarRol(ContextoTienda contexto, UUID usuarioId, CambiarRolRequest request) {
        RolTienda rolNuevo = rolSolicitado(request.rol());
        MiembroPersonalVista objetivo = objetivoGestionable(contexto, usuarioId);
        RolTienda rolActual = RolTienda.desdeCodigo(objetivo.getRol()).orElseThrow();
        exigirPuedeGestionar(contexto, rolActual);
        exigirPuedeGestionar(contexto, rolNuevo);

        if (rolActual == rolNuevo) {
            return mapeador.aResponse(objetivo);
        }

        PersonalTiendaEntidad fila = personalRepositorio
                .findByTiendaIdAndUsuarioIdAndEliminadoFalse(contexto.tiendaId(), usuarioId)
                .orElseThrow(this::noEncontrado);
        fila.setRolId(rol(rolNuevo).getId());
        personalRepositorio.saveAndFlush(fila);

        auditoriaServicio.registrar(contexto.tiendaId(), AccionAuditoria.PERSONAL_ROL_CAMBIADO, ENTIDAD, usuarioId,
                request.motivo().trim(), Map.of("antes", rolActual.name(), "despues", rolNuevo.name()));
        return miembro(contexto.tiendaId(), usuarioId);
    }

    /** Eliminación lógica; mismas validaciones que cambiar el rol. */
    @Transactional
    public void quitar(ContextoTienda contexto, UUID usuarioId, QuitarPersonalRequest request) {
        MiembroPersonalVista objetivo = objetivoGestionable(contexto, usuarioId);
        RolTienda rolActual = RolTienda.desdeCodigo(objetivo.getRol()).orElseThrow();
        exigirPuedeGestionar(contexto, rolActual);

        PersonalTiendaEntidad fila = personalRepositorio
                .findByTiendaIdAndUsuarioIdAndEliminadoFalse(contexto.tiendaId(), usuarioId)
                .orElseThrow(this::noEncontrado);
        fila.setEliminado(true);
        personalRepositorio.saveAndFlush(fila);

        auditoriaServicio.registrar(contexto.tiendaId(), AccionAuditoria.PERSONAL_QUITADO, ENTIDAD, usuarioId,
                request.motivo().trim(), Map.of("correo", objetivo.getCorreoElectronico(), "rol", rolActual.name()));
    }

    /** PER-404, PER-007 y PER-003 sobre el miembro objetivo. */
    private MiembroPersonalVista objetivoGestionable(ContextoTienda contexto, UUID usuarioId) {
        MiembroPersonalVista objetivo = personalRepositorio.buscarMiembro(contexto.tiendaId(), usuarioId)
                .orElseThrow(this::noEncontrado);
        if (usuarioId.equals(contexto.usuarioId())) {
            throw new ReglaNegocioException("PER-007", "No puedes cambiar tu propio rol ni quitarte del personal");
        }
        if (RolTienda.PROPIETARIO.name().equals(objetivo.getRol())) {
            throw rolPropietario();
        }
        return objetivo;
    }

    /** PER-004: un administrador solo gestiona roles de gestor. */
    private void exigirPuedeGestionar(ContextoTienda contexto, RolTienda rolAfectado) {
        if (contexto.rol() == RolTienda.ADMINISTRADOR && rolAfectado == RolTienda.ADMINISTRADOR) {
            throw new AccesoDenegadoException("PER-004", "Solo el propietario puede gestionar a un administrador");
        }
    }

    /** PER-002: personal sin contar al propietario por debajo del límite del plan; sin suscripción vigente el límite es 0. */
    private void exigirCupo(UUID tiendaId) {
        int limite = suscripcionServicio.vigente(tiendaId).map(SuscripcionVigente::limitePersonal).orElse(0);
        if (personalRepositorio.contarSinPropietario(tiendaId) >= limite) {
            throw new ReglaNegocioException("PER-002",
                    "Tu plan permite hasta " + limite + " personas en el personal. Mejora tu plan para agregar más");
        }
    }

    /** PER-003: el rol PROPIETARIO no se asigna por esta vía. */
    private RolTienda rolSolicitado(String codigo) {
        RolTienda rol = RolTienda.desdeCodigo(codigo)
                .orElseThrow(() -> new ReglaNegocioException("PER-003", "Ese rol no se puede asignar"));
        if (rol == RolTienda.PROPIETARIO) {
            throw rolPropietario();
        }
        return rol;
    }

    private RolEntidad rol(RolTienda rol) {
        return rolRepositorio.findByCodigoAndEliminadoFalse(rol.name())
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + rol + " en el catálogo de roles"));
    }

    private MiembroPersonalResponse miembro(UUID tiendaId, UUID usuarioId) {
        return mapeador.aResponse(personalRepositorio.buscarMiembro(tiendaId, usuarioId).orElseThrow(this::noEncontrado));
    }

    private RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("PER-404", "No encontramos a esa persona en el personal");
    }

    private ReglaNegocioException rolPropietario() {
        return new ReglaNegocioException("PER-003", "El rol de propietario no se asigna, cambia ni quita");
    }

    private ConflictoEstadoException yaEsPersonal() {
        return new ConflictoEstadoException("PER-006", "Esa persona ya forma parte del personal de la tienda");
    }
}
