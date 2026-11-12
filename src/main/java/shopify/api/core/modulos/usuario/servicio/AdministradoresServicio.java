package shopify.api.core.modulos.usuario.servicio;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.comun.seguridad.UsuarioAutenticado;
import shopify.api.core.modulos.usuario.dto.request.AdministradoresRequests.EditarRolPlataformaRequest;
import shopify.api.core.modulos.usuario.dto.request.AdministradoresRequests.RolPlataformaRequest;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.AdministradorResponse;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.PermisoPlataformaResponse;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.RolPlataformaResponse;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.RolResumen;
import shopify.api.core.modulos.usuario.entidad.RolPlataformaEntidad;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.repositorio.RolPlataformaRepositorio;
import shopify.api.core.modulos.usuario.repositorio.UsuarioRepositorio;

/**
 * Roles y administradores de plataforma (R-001, B9 etapa 1). Reglas: nadie asigna, concede ni quita permisos que no
 * tiene; nadie modifica su propia cuenta de administrador; el superadministrador (bandera) no se toca por la API; y
 * la plataforma nunca se queda sin quien gestione administradores. Todo cambio queda auditado como evento de seguridad.
 * Los permisos efectivos se leen de la base en cada petición, así que un cambio rige de inmediato.
 */
@Service
@RequiredArgsConstructor
public class AdministradoresServicio {

    private static final Map<PermisoPlataforma, String> DESCRIPCIONES = Map.of(
            PermisoPlataforma.PLATAFORMA_TIENDAS, "Ver tiendas y suscripciones",
            PermisoPlataforma.PLATAFORMA_SUSPENDER_TIENDAS, "Suspender y reactivar tiendas",
            PermisoPlataforma.PLATAFORMA_USUARIOS, "Ver usuarios",
            PermisoPlataforma.PLATAFORMA_PLANES, "Gestionar planes de suscripción",
            PermisoPlataforma.PLATAFORMA_PEDIDOS, "Ver pedidos, repartos y comisiones",
            PermisoPlataforma.PLATAFORMA_AUDITORIA, "Ver la auditoría",
            PermisoPlataforma.PLATAFORMA_SEGURIDAD, "Bloquear usuarios y cerrar sesiones",
            PermisoPlataforma.PLATAFORMA_ADMINISTRADORES, "Gestionar roles y administradores");

    private final RolPlataformaRepositorio roles;
    private final UsuarioRepositorio usuarios;
    private final AuditoriaServicio auditoria;

    // ------------------------------------------------------------------ catálogo y roles

    public List<PermisoPlataformaResponse> permisos() {
        return java.util.Arrays.stream(PermisoPlataforma.values())
                .map(p -> new PermisoPlataformaResponse(p.name(), DESCRIPCIONES.getOrDefault(p, p.name()))).toList();
    }

    @Transactional(readOnly = true)
    public List<RolPlataformaResponse> roles() {
        return roles.findByEliminadoFalseOrderByNombreAsc().stream().map(this::aRol).toList();
    }

    @Transactional
    public RolPlataformaResponse crearRol(RolPlataformaRequest solicitud) {
        Set<PermisoPlataforma> nuevos = permisosDe(solicitud.permisos());
        exigirQueElActorLosTiene(nuevos);
        if (roles.existeCodigo(solicitud.codigo())) {
            throw new ConflictoEstadoException("ADM-006", "Ya existe un rol con ese código");
        }
        RolPlataformaEntidad rol = new RolPlataformaEntidad();
        rol.setCodigo(solicitud.codigo());
        rol.setNombre(solicitud.nombre().strip());
        rol.setDescripcion(solicitud.descripcion() == null || solicitud.descripcion().isBlank() ? null : solicitud.descripcion().strip());
        rol.setEsSistema(false);
        rol.setPermisos(nombres(nuevos));
        roles.saveAndFlush(rol);
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_ROL_PLATAFORMA_CREADO, "RolesPlataforma", rol.getId(), null,
                Map.of("codigo", rol.getCodigo(), "permisos", ordenados(nuevos)));
        return aRol(rol);
    }

    @Transactional
    public RolPlataformaResponse editarRol(UUID rolId, EditarRolPlataformaRequest solicitud) {
        RolPlataformaEntidad rol = rol(rolId);
        if (Boolean.TRUE.equals(rol.getEsSistema())) {
            throw new ConflictoEstadoException("ADM-004", "Los roles de sistema no se editan");
        }
        Set<PermisoPlataforma> antes = permisosDe(List.copyOf(rol.getPermisos()));
        Set<PermisoPlataforma> despues = permisosDe(solicitud.permisos());
        Set<PermisoPlataforma> tocados = EnumSet.noneOf(PermisoPlataforma.class);
        tocados.addAll(antes);
        tocados.addAll(despues);
        exigirQueElActorLosTiene(tocados);
        if (antes.contains(PermisoPlataforma.PLATAFORMA_ADMINISTRADORES)
                && !despues.contains(PermisoPlataforma.PLATAFORMA_ADMINISTRADORES)
                && rolDelActorEs(rolId)) {
            throw new ConflictoEstadoException("ADM-003", "No puedes quitarle ese permiso al rol que te lo da a ti");
        }
        rol.setNombre(solicitud.nombre().strip());
        rol.setDescripcion(solicitud.descripcion() == null || solicitud.descripcion().isBlank() ? null : solicitud.descripcion().strip());
        rol.setPermisos(nombres(despues));
        roles.saveAndFlush(rol);
        exigirTitularesDeAdministradores();
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_ROL_PLATAFORMA_EDITADO, "RolesPlataforma", rolId, null,
                Map.of("antes", ordenados(antes), "despues", ordenados(despues)));
        return aRol(rol);
    }

    @Transactional
    public void eliminarRol(UUID rolId) {
        RolPlataformaEntidad rol = rol(rolId);
        if (Boolean.TRUE.equals(rol.getEsSistema())) {
            throw new ConflictoEstadoException("ADM-004", "Los roles de sistema no se eliminan");
        }
        if (roles.contarUsuariosConRol(rolId) > 0) {
            throw new ConflictoEstadoException("ADM-005", "Hay administradores con este rol: reasígnalos primero");
        }
        rol.setEliminado(true);
        roles.save(rol);
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_ROL_PLATAFORMA_ELIMINADO, "RolesPlataforma", rolId, null,
                Map.of("codigo", rol.getCodigo()));
    }

    // ------------------------------------------------------------------ administradores

    @Transactional(readOnly = true)
    public List<AdministradorResponse> administradores() {
        Map<UUID, RolPlataformaEntidad> porId = roles.findByEliminadoFalseOrderByNombreAsc().stream()
                .collect(Collectors.toMap(RolPlataformaEntidad::getId, r -> r));
        return roles.listarAdministradores().stream().map(u -> aAdministrador(u, porId.get(u.getRolPlataformaId()))).toList();
    }

    @Transactional
    public AdministradorResponse agregar(UUID usuarioId, UUID rolId) {
        UsuarioEntidad usuario = usuario(usuarioId);
        exigirNoEsElActor(usuarioId);
        if (Boolean.TRUE.equals(usuario.getEsAdministradorPlataforma()) || usuario.getRolPlataformaId() != null) {
            throw new ConflictoEstadoException("ADM-007", "Esa persona ya es administradora: cambia su rol");
        }
        if (!Boolean.TRUE.equals(usuario.getEstaActivo())) {
            throw new ConflictoEstadoException("ADM-008", "La cuenta no está activa");
        }
        RolPlataformaEntidad rol = rol(rolId);
        exigirQueElActorLosTiene(permisosDe(List.copyOf(rol.getPermisos())));
        usuario.setRolPlataformaId(rolId);
        usuarios.saveAndFlush(usuario);
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_ADMIN_AGREGADO, "Usuarios", usuarioId, null,
                Map.of("correo", usuario.getCorreoElectronico(), "rol", rol.getCodigo()));
        return aAdministrador(usuario, rol);
    }

    @Transactional
    public AdministradorResponse cambiarRol(UUID usuarioId, UUID rolId) {
        UsuarioEntidad usuario = usuario(usuarioId);
        exigirEsAdministradorConRol(usuario);
        exigirNoEsElActor(usuarioId);
        RolPlataformaEntidad anterior = rol(usuario.getRolPlataformaId());
        RolPlataformaEntidad nuevo = rol(rolId);
        Set<PermisoPlataforma> tocados = EnumSet.noneOf(PermisoPlataforma.class);
        tocados.addAll(permisosDe(List.copyOf(anterior.getPermisos())));
        tocados.addAll(permisosDe(List.copyOf(nuevo.getPermisos())));
        exigirQueElActorLosTiene(tocados);
        usuario.setRolPlataformaId(rolId);
        usuarios.saveAndFlush(usuario);
        exigirTitularesDeAdministradores();
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_ADMIN_ROL_CAMBIADO, "Usuarios", usuarioId, null,
                Map.of("correo", usuario.getCorreoElectronico(), "antes", anterior.getCodigo(), "despues", nuevo.getCodigo()));
        return aAdministrador(usuario, nuevo);
    }

    @Transactional
    public void quitar(UUID usuarioId) {
        UsuarioEntidad usuario = usuario(usuarioId);
        exigirEsAdministradorConRol(usuario);
        exigirNoEsElActor(usuarioId);
        RolPlataformaEntidad rol = rol(usuario.getRolPlataformaId());
        exigirQueElActorLosTiene(permisosDe(List.copyOf(rol.getPermisos())));
        usuario.setRolPlataformaId(null);
        usuarios.saveAndFlush(usuario);
        exigirTitularesDeAdministradores();
        auditoria.registrar(null, AccionAuditoria.SEGURIDAD_ADMIN_QUITADO, "Usuarios", usuarioId, null,
                Map.of("correo", usuario.getCorreoElectronico(), "rol", rol.getCodigo()));
    }

    // ------------------------------------------------------------------ reglas

    /** Nadie concede ni toca permisos que él mismo no tiene. */
    private static void exigirQueElActorLosTiene(Set<PermisoPlataforma> permisos) {
        UsuarioAutenticado actor = SesionActual.usuario()
                .orElseThrow(() -> new AccesoDenegadoException("SEG-002", "Tu rol no tiene permiso para esta acción"));
        for (PermisoPlataforma permiso : permisos) {
            if (!actor.tiene(permiso)) {
                throw new AccesoDenegadoException("ADM-001", "No puedes asignar permisos que tú no tienes");
            }
        }
    }

    private static void exigirNoEsElActor(UUID usuarioId) {
        if (usuarioId.equals(SesionActual.usuarioId())) {
            throw new ConflictoEstadoException("ADM-003", "No puedes cambiar tu propia cuenta de administrador");
        }
    }

    private static void exigirEsAdministradorConRol(UsuarioEntidad usuario) {
        if (Boolean.TRUE.equals(usuario.getEsAdministradorPlataforma())) {
            throw new ConflictoEstadoException("ADM-002", "El superadministrador no se modifica desde la API");
        }
        if (usuario.getRolPlataformaId() == null) {
            throw new ConflictoEstadoException("ADM-009", "Esa persona no es administradora con rol");
        }
    }

    /** Si el cambio dejó a la plataforma sin quien gestione administradores, se deshace (la transacción falla). */
    private void exigirTitularesDeAdministradores() {
        if (roles.contarTitularesDeAdministradores() < 1) {
            throw new ConflictoEstadoException("ADM-003",
                    "La plataforma no puede quedarse sin nadie que gestione administradores");
        }
    }

    private boolean rolDelActorEs(UUID rolId) {
        return usuarios.findById(SesionActual.usuarioId()).map(u -> rolId.equals(u.getRolPlataformaId())).orElse(false);
    }

    // ------------------------------------------------------------------ utilidades

    private RolPlataformaEntidad rol(UUID id) {
        return roles.findByIdAndEliminadoFalse(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("ADM-404", "No encontramos ese rol"));
    }

    private UsuarioEntidad usuario(UUID id) {
        return usuarios.findById(id).filter(u -> !Boolean.TRUE.equals(u.getEliminado()))
                .orElseThrow(() -> new RecursoNoEncontradoException("USR-404", "No encontramos ese usuario"));
    }

    /** Solo permisos del catálogo; uno desconocido es 400. */
    private static Set<PermisoPlataforma> permisosDe(List<String> nombres) {
        Set<PermisoPlataforma> resultado = EnumSet.noneOf(PermisoPlataforma.class);
        for (String nombre : nombres) {
            try {
                resultado.add(PermisoPlataforma.valueOf(nombre.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new SolicitudInvalidaException("VAL-003", "Permiso desconocido: " + nombre);
            }
        }
        return resultado;
    }

    private static Set<String> nombres(Set<PermisoPlataforma> permisos) {
        return permisos.stream().map(Enum::name).collect(Collectors.toCollection(java.util.HashSet::new));
    }

    private static List<String> ordenados(Set<PermisoPlataforma> permisos) {
        return permisos.stream().map(Enum::name).sorted().toList();
    }

    private RolPlataformaResponse aRol(RolPlataformaEntidad rol) {
        return new RolPlataformaResponse(rol.getId(), rol.getCodigo(), rol.getNombre(), rol.getDescripcion(),
                Boolean.TRUE.equals(rol.getEsSistema()), rol.getPermisos().stream().sorted().toList(),
                roles.contarUsuariosConRol(rol.getId()));
    }

    private AdministradorResponse aAdministrador(UsuarioEntidad usuario, RolPlataformaEntidad rol) {
        boolean superadmin = Boolean.TRUE.equals(usuario.getEsAdministradorPlataforma());
        List<String> permisos = superadmin ? PermisoPlataforma.nombresDeTodos()
                : rol == null ? List.of() : rol.getPermisos().stream().sorted().toList();
        return new AdministradorResponse(usuario.getId(), usuario.getCorreoElectronico(),
                usuario.getNombres() + " " + usuario.getApellidos(), superadmin,
                rol == null ? null : new RolResumen(rol.getId(), rol.getCodigo(), rol.getNombre()), permisos);
    }
}
