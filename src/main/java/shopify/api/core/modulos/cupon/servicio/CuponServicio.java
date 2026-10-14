package shopify.api.core.modulos.cupon.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.cupon.dto.request.CuponRequest;
import shopify.api.core.modulos.cupon.dto.response.CuponResponse;
import shopify.api.core.modulos.cupon.entidad.CuponEntidad;
import shopify.api.core.modulos.cupon.mapeador.CuponMapeador;
import shopify.api.core.modulos.cupon.modelo.CuponAplicable;
import shopify.api.core.modulos.cupon.modelo.TipoDescuento;
import shopify.api.core.modulos.cupon.modelo.ValidadorCupon;
import shopify.api.core.modulos.cupon.repositorio.CuponRepositorio;

/** Cupones de la tienda: CRUD del panel (CUP-001 a CUP-009) y operaciones internas de carrito y checkout. */
@Service
@RequiredArgsConstructor
public class CuponServicio {

    static final int TAMANO_PAGINA = 20;

    private static final Pattern CODIGO = Pattern.compile("^[A-Z0-9_-]{3,50}$");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final CuponRepositorio repositorio;
    private final CuponMapeador mapeador;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public Page<CuponResponse> listar(ContextoTienda contexto, int pagina, String texto, Boolean activo) {
        return repositorio.buscar(contexto.tiendaId(), patronDeBusqueda(texto), activo,
                        PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaCreacion")))
                .map(mapeador::aResponse);
    }

    @Transactional(readOnly = true)
    public CuponResponse obtener(ContextoTienda contexto, UUID id) {
        return mapeador.aResponse(buscarPropio(contexto, id));
    }

    @Transactional
    public CuponResponse crear(ContextoTienda contexto, CuponRequest request) {
        String codigo = normalizarCodigo(request.codigo());
        exigirCodigoValido(codigo);
        Instant inicio = request.fechaInicio() == null ? reloj.instant() : request.fechaInicio();
        BigDecimal valor = Dinero.redondear(request.valorDescuento());
        exigirConfiguracion(request.tipoDescuento(), valor, inicio, request.fechaFin());

        if (repositorio.existsByTiendaIdAndCodigoAndEliminadoFalse(contexto.tiendaId(), codigo)) {
            throw codigoDuplicado();
        }

        CuponEntidad cupon = new CuponEntidad();
        cupon.setTiendaId(contexto.tiendaId());
        cupon.setCodigo(codigo);
        cupon.setTipoDescuento(request.tipoDescuento());
        cupon.setValorDescuento(valor);
        cupon.setMontoMinimoCompra(montoMinimo(request.montoMinimoCompra()));
        cupon.setLimiteUsosTotal(request.limiteUsosTotal());
        cupon.setFechaInicio(inicio);
        cupon.setFechaFin(request.fechaFin());
        cupon.setEstaActivo(request.estaActivo() == null || request.estaActivo());
        try {
            return mapeador.aResponse(repositorio.saveAndFlush(cupon));
        } catch (DataIntegrityViolationException ex) {
            // Carrera: UxCuponesTiendaCodigo
            throw codigoDuplicado();
        }
    }

    /** CUP-008: si el cupón ya tiene usos, no se cambia su tipo ni su valor. */
    @Transactional
    public CuponResponse actualizar(ContextoTienda contexto, UUID id, CuponRequest request) {
        CuponEntidad cupon = buscarPropio(contexto, id);
        String codigo = normalizarCodigo(request.codigo());
        exigirCodigoValido(codigo);
        if (repositorio.existsByTiendaIdAndCodigoAndEliminadoFalseAndIdNot(contexto.tiendaId(), codigo, id)) {
            throw codigoDuplicado();
        }

        Instant inicio = request.fechaInicio() == null ? cupon.getFechaInicio() : request.fechaInicio();
        BigDecimal valor = Dinero.redondear(request.valorDescuento());
        exigirConfiguracion(request.tipoDescuento(), valor, inicio, request.fechaFin());

        boolean cambiaTipoOValor = request.tipoDescuento() != cupon.getTipoDescuento()
                || valor.compareTo(cupon.getValorDescuento()) != 0;
        if (cambiaTipoOValor && cupon.getCantidadUsosActual() > 0) {
            throw new ConflictoEstadoException("CUP-008",
                    "Este cupón ya se usó: no se puede cambiar su tipo ni su valor. Crea otro cupón");
        }

        cupon.setCodigo(codigo);
        cupon.setTipoDescuento(request.tipoDescuento());
        cupon.setValorDescuento(valor);
        cupon.setMontoMinimoCompra(montoMinimo(request.montoMinimoCompra()));
        cupon.setLimiteUsosTotal(request.limiteUsosTotal());
        cupon.setFechaInicio(inicio);
        cupon.setFechaFin(request.fechaFin());
        if (request.estaActivo() != null) {
            cupon.setEstaActivo(request.estaActivo());
        }
        try {
            return mapeador.aResponse(repositorio.saveAndFlush(cupon));
        } catch (DataIntegrityViolationException ex) {
            // Carrera: UxCuponesTiendaCodigo
            throw codigoDuplicado();
        }
    }

    /** Eliminación lógica: el cupón puede tener usos registrados (CUP-008). */
    @Transactional
    public void eliminar(ContextoTienda contexto, UUID id) {
        CuponEntidad cupon = buscarPropio(contexto, id);
        cupon.setEliminado(true);
        repositorio.save(cupon);
    }

    /**
     * Cupón que aplica al subtotal dado (CUP-003). El código se busca sin distinguir mayúsculas.
     * Si no aplica, lanza CUP-003 con el motivo concreto (CUP-006).
     */
    @Transactional(readOnly = true)
    public CuponAplicable evaluar(UUID tiendaId, String codigo, BigDecimal subtotal) {
        String normalizado = normalizarCodigo(codigo);
        CuponEntidad cupon = repositorio.findByTiendaIdAndCodigoAndEliminadoFalse(tiendaId, normalizado)
                .orElseThrow(() -> new ReglaNegocioException("CUP-003", "El cupón no existe"));

        ValidadorCupon.motivoNoAplicable(cupon, subtotal, reloj.instant())
                .ifPresent(motivo -> {
                    throw new ReglaNegocioException("CUP-003", motivo);
                });
        return new CuponAplicable(cupon.getId(), cupon.getCodigo(), cupon.getTipoDescuento(), cupon.getValorDescuento());
    }

    /** CUP-007: consume un uso con un UPDATE condicional; si no queda ninguno, CHK-005. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consumirUso(UUID cuponId) {
        if (repositorio.consumirUso(cuponId, reloj.instant()) == 0) {
            throw new ConflictoEstadoException("CHK-005", "El cupón ya no está disponible");
        }
    }

    /** Repone un uso (orden anulada por falta de pago, D-020); nunca baja de cero. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void devolverUso(UUID cuponId) {
        repositorio.devolverUso(cuponId);
    }

    private CuponEntidad buscarPropio(ContextoTienda contexto, UUID id) {
        return repositorio.findByIdAndTiendaIdAndEliminadoFalse(id, contexto.tiendaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CUP-404", "No encontramos ese cupón"));
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    }

    private static void exigirCodigoValido(String codigo) {
        if (!CODIGO.matcher(codigo).matches()) {
            throw new ReglaNegocioException("CUP-001",
                    "El código debe tener de 3 a 50 caracteres: letras, números, guion o guion bajo");
        }
    }

    private static ReglaNegocioException codigoDuplicado() {
        return new ReglaNegocioException("CUP-001", "Ya existe un cupón con ese código en tu tienda");
    }

    /** CUP-002 (valor según el tipo) y CUP-009 (fechas). */
    private static void exigirConfiguracion(TipoDescuento tipo, BigDecimal valor, Instant inicio, Instant fin) {
        boolean valido = switch (tipo) {
            case PORCENTAJE -> valor.signum() > 0 && valor.compareTo(CIEN) <= 0;
            case MONTO_FIJO -> valor.signum() > 0;
            case ENVIO_GRATIS -> valor.signum() == 0;
        };
        if (!valido) {
            throw new ReglaNegocioException("CUP-002", switch (tipo) {
                case PORCENTAJE -> "El porcentaje debe ser mayor que 0 y no superar 100";
                case MONTO_FIJO -> "El monto fijo debe ser mayor que 0";
                case ENVIO_GRATIS -> "El envío gratis no lleva valor: debe ser 0";
            });
        }
        if (fin != null && !fin.isAfter(inicio)) {
            throw new ReglaNegocioException("CUP-009", "La fecha de fin debe ser posterior a la de inicio");
        }
    }

    private static BigDecimal montoMinimo(BigDecimal monto) {
        return monto == null ? Dinero.CERO : Dinero.redondear(monto);
    }

    private static String patronDeBusqueda(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String escapado = texto.strip().toUpperCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapado + "%";
    }
}
