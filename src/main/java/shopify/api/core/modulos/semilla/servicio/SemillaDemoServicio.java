package shopify.api.core.modulos.semilla.servicio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.modulos.balance.servicio.BalanceServicio;
import shopify.api.core.modulos.balance.servicio.RepartoServicio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.orden.modelo.NuevaLinea;
import shopify.api.core.modulos.orden.modelo.NuevaOrden;
import shopify.api.core.modulos.orden.modelo.NuevoPedido;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.orden.servicio.PedidoServicio;
import shopify.api.core.modulos.semilla.servicio.DatosDemo.FotoDemo;
import shopify.api.core.modulos.semilla.servicio.DatosDemo.ProductoDemo;
import shopify.api.core.modulos.semilla.servicio.DatosDemo.TiendaDemo;
import shopify.api.core.modulos.semilla.servicio.DatosDemo.VarianteDemo;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;
import shopify.api.core.modulos.tienda.modelo.ConfiguracionVenta;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;
import shopify.api.core.modulos.totales.modelo.CalculadoraTotales;
import shopify.api.core.modulos.totales.modelo.EnvioCalculo;
import shopify.api.core.modulos.totales.modelo.ImpuestoCalculo;
import shopify.api.core.modulos.totales.modelo.LineaCalculo;
import shopify.api.core.modulos.totales.modelo.TotalesCalculados;

/**
 * Semilla de datos de demostración (R-014): cuentas, seis tiendas en soles con catálogo, tarifas, pedidos de ejemplo
 * y reseñas. Es idempotente: cada pieza se busca por su clave natural (correo, subdominio, slug, clave del pedido) y
 * se omite si ya existe; no toca ni borra nada que no sea suyo. Los pedidos, el inventario, el pago y el balance pasan
 * por los servicios del dominio (con sus reglas); el catálogo, las cuentas y las suscripciones, por JDBC porque esos
 * servicios dependen de una sesión HTTP. No envía correos ni crea intentos de Stripe. La contraseña llega por
 * parámetro y nunca se escribe en el log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemillaDemoServicio {

    static final String MONEDA = "PEN";
    private static final int LARGO_MINIMO_CONTRASENA = 8;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaccion;
    private final TiendaServicio tiendas;
    private final BalanceServicio balance;
    private final OrdenServicio ordenes;
    private final PedidoServicio pedidos;
    private final InventarioServicio inventario;
    private final SuscripcionServicio suscripciones;
    private final RepartoServicio repartos;
    private final GeneradorToken generadorToken;
    private final Clock reloj;

    /** Cuántas piezas se crearon y cuántas ya existían, por categoría. */
    public record Resumen(Map<String, Integer> creados, Map<String, Integer> omitidos) {

        int creados(String clave) {
            return creados.getOrDefault(clave, 0);
        }
    }

    private enum Estado { PENDIENTE, PAGADO, PREPARADO, ENTREGADO }

    private record Cuentas(UUID admin, UUID propietario1, UUID propietario2, UUID comprador) {

        UUID propietario(int indice) {
            return indice == 0 ? propietario1 : propietario2;
        }
    }

    /** Un tramo de un pedido: una tienda, sus productos (por posición en el catálogo) y el estado que debe tener. */
    private record Tramo(String subdominio, List<int[]> productos, Estado estado) {
    }

    private record Contador(Map<String, Integer> creados, Map<String, Integer> omitidos) {

        void creado(String clave) {
            creados.merge(clave, 1, Integer::sum);
        }

        void omitido(String clave) {
            omitidos.merge(clave, 1, Integer::sum);
        }
    }

    // ------------------------------------------------------------------ entrada

    public Resumen ejecutar(String contrasena, String correoAdmin) {
        if (contrasena == null || contrasena.length() < LARGO_MINIMO_CONTRASENA) {
            throw new IllegalArgumentException("La contraseña de la semilla debe tener al menos " + LARGO_MINIMO_CONTRASENA + " caracteres");
        }
        Contador contador = new Contador(new LinkedHashMap<>(), new LinkedHashMap<>());
        String hash = passwordEncoder.encode(contrasena);

        Cuentas cuentas = transaccion.execute(estado -> cuentas(hash, correoAdmin, contador));
        Map<String, UUID> tiendaIds = new LinkedHashMap<>();
        for (TiendaDemo tienda : DatosDemo.TIENDAS) {
            UUID id = transaccion.execute(estado -> tienda(tienda, cuentas.propietario(tienda.propietario()), contador));
            tiendaIds.put(tienda.subdominio(), id);
        }
        for (Map.Entry<String, List<Tramo>> pedido : pedidosDeEjemplo().entrySet()) {
            transaccion.executeWithoutResult(estado -> pedido(pedido.getKey(), usuarioDe(pedido.getKey(), cuentas),
                    pedido.getValue(), tiendaIds, contador));
        }
        transaccion.executeWithoutResult(estado -> resenas(cuentas, tiendaIds, contador));

        log.info("Semilla de demostración: creados {}, omitidos {}", contador.creados(), contador.omitidos());
        return new Resumen(contador.creados(), contador.omitidos());
    }

    // ------------------------------------------------------------------ cuentas

    private Cuentas cuentas(String hash, String correoAdmin, Contador contador) {
        UUID rolAdmin = jdbc.queryForList("SELECT \"Id\" FROM \"RolesPlataforma\" WHERE \"Codigo\" = 'ADMIN_PLATAFORMA' "
                + "AND \"Eliminado\" = FALSE", UUID.class).stream().findFirst().orElse(null);
        UUID admin = usuario(correoAdmin, "Admin", "Demo", hash, rolAdmin, contador);
        UUID p1 = usuario("propietario.demo@otemu.test", "Paula", "Propietaria", hash, null, contador);
        UUID p2 = usuario("propietario2.demo@otemu.test", "Pedro", "Propietario", hash, null, contador);
        UUID comprador = usuario("comprador.demo@otemu.test", "Camila", "Compradora", hash, null, contador);
        return new Cuentas(admin, p1, p2, comprador);
    }

    private UUID usuario(String correo, String nombres, String apellidos, String hash, UUID rolPlataforma, Contador contador) {
        Optional<UUID> existente = jdbc.queryForList("SELECT \"Id\" FROM \"Usuarios\" WHERE lower(\"CorreoElectronico\") = lower(?)",
                UUID.class, correo).stream().findFirst();
        if (existente.isPresent()) {
            contador.omitido("cuentas");
            return existente.get();
        }
        contador.creado("cuentas");
        return jdbc.queryForObject("INSERT INTO \"Usuarios\" (\"CorreoElectronico\", \"ContrasenaHash\", \"Nombres\", \"Apellidos\", "
                + "\"EstaActivo\", \"RolPlataformaId\") VALUES (?, ?, ?, ?, TRUE, ?) RETURNING \"Id\"", UUID.class,
                correo.toLowerCase(), hash, nombres, apellidos, rolPlataforma);
    }

    // ------------------------------------------------------------------ tiendas y catálogo

    private UUID tienda(TiendaDemo demo, UUID propietario, Contador contador) {
        Optional<UUID> existente = jdbc.queryForList("SELECT \"Id\" FROM \"Tiendas\" WHERE \"Subdominio\" = ? AND \"Eliminado\" = FALSE",
                UUID.class, demo.subdominio()).stream().findFirst();
        UUID tiendaId;
        if (existente.isPresent()) {
            tiendaId = existente.get();
            contador.omitido("tiendas");
        } else {
            tiendaId = jdbc.queryForObject("INSERT INTO \"Tiendas\" (\"PropietarioId\", \"Nombre\", \"Subdominio\", \"CorreoContacto\", "
                    + "\"Moneda\", \"Descripcion\", \"UrlLogo\", \"VisibleEnMercado\", \"EstaActiva\") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, TRUE) RETURNING \"Id\"", UUID.class, propietario, demo.nombre(),
                    demo.subdominio(), "contacto@" + demo.subdominio() + ".otemu.test", MONEDA, demo.descripcion(),
                    "https://picsum.photos/seed/" + demo.subdominio() + "-logo/240/240");
            contador.creado("tiendas");
        }
        suscripcion(tiendaId, demo.plan(), contador);
        balance.crearSiNoExiste(tiendaId);

        List<UUID> categorias = new ArrayList<>();
        for (String nombre : demo.categorias()) {
            categorias.add(categoria(tiendaId, nombre, contador));
        }
        for (ProductoDemo producto : demo.productos()) {
            producto(tiendaId, demo, producto, categorias.get(producto.categoria()), contador);
        }
        tarifa(tiendaId, "Estándar", "8.00", "150.00", 1, contador);
        tarifa(tiendaId, "Exprés", "15.00", null, 2, contador);
        return tiendaId;
    }

    private void suscripcion(UUID tiendaId, String codigoPlan, Contador contador) {
        boolean tiene = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM \"SuscripcionesTienda\" "
                + "WHERE \"TiendaId\" = ? AND \"Estado\" <> 'CANCELADA' AND \"Eliminado\" = FALSE)", Boolean.class, tiendaId));
        if (tiene) {
            contador.omitido("suscripciones");
            return;
        }
        List<Map<String, Object>> plan = jdbc.queryForList("SELECT \"Id\", \"PrecioMensual\" FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = ? "
                + "AND \"Eliminado\" = FALSE", codigoPlan);
        if (plan.isEmpty()) {
            log.warn("Semilla: no existe el plan {}; la tienda queda sin suscripción", codigoPlan);
            return;
        }
        boolean gratis = "GRATIS".equals(codigoPlan);
        Instant ahora = reloj.instant();
        Instant fin = gratis ? Instant.parse("9999-12-31T00:00:00Z") : ahora.plus(Duration.ofDays(365));
        jdbc.update("INSERT INTO \"SuscripcionesTienda\" (\"TiendaId\", \"PlanId\", \"Estado\", \"PrecioPactado\", \"InicioPeriodoActual\", "
                + "\"FinPeriodoActual\") VALUES (?, ?, 'ACTIVA', ?, ?, ?)", tiendaId, plan.get(0).get("Id"),
                plan.get(0).get("PrecioMensual"), Timestamp.from(ahora), Timestamp.from(fin));
        contador.creado("suscripciones");
    }

    private UUID categoria(UUID tiendaId, String nombre, Contador contador) {
        String slug = DatosDemo.slug(nombre);
        Optional<UUID> existente = jdbc.queryForList("SELECT \"Id\" FROM \"Categorias\" WHERE \"TiendaId\" = ? AND \"IdentificadorUrl\" = ? "
                + "AND \"Eliminado\" = FALSE", UUID.class, tiendaId, slug).stream().findFirst();
        if (existente.isPresent()) {
            contador.omitido("categorias");
            return existente.get();
        }
        contador.creado("categorias");
        return jdbc.queryForObject("INSERT INTO \"Categorias\" (\"TiendaId\", \"Nombre\", \"IdentificadorUrl\") VALUES (?, ?, ?) RETURNING \"Id\"",
                UUID.class, tiendaId, nombre, slug);
    }

    private void producto(UUID tiendaId, TiendaDemo tienda, ProductoDemo demo, UUID categoriaId, Contador contador) {
        String slug = demo.slug();
        boolean existe = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM \"Productos\" WHERE \"TiendaId\" = ? "
                + "AND \"IdentificadorUrl\" = ? AND \"Eliminado\" = FALSE)", Boolean.class, tiendaId, slug));
        if (existe) {
            contador.omitido("productos");
            return;
        }
        UUID productoId = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", \"Descripcion\", "
                + "\"EsDigital\", \"EstaPublicado\") VALUES (?, ?, ?, ?, ?, TRUE) RETURNING \"Id\"", UUID.class, tiendaId,
                demo.titulo(), slug, demo.descripcion(), demo.digital());
        jdbc.update("INSERT INTO \"ProductosCategorias\" (\"ProductoId\", \"CategoriaId\") VALUES (?, ?)", productoId, categoriaId);

        List<VarianteDemo> variantes = variantesDe(demo);
        Map<String, UUID> idsPorTitulo = new LinkedHashMap<>();
        int indice = 1;
        for (VarianteDemo variante : variantes) {
            UUID varianteId = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\", "
                    + "\"PrecioComparacion\", \"RequiereEnvio\", \"FechaCreacion\") VALUES (?, ?, ?, ?, ?, ?, clock_timestamp()) "
                    + "RETURNING \"Id\"", UUID.class, productoId,
                    variante.titulo(),
                    tienda.subdominio().toUpperCase().replace("-", "").substring(0, 4) + "-" + Math.abs(slug.hashCode() % 10000) + "-" + indice++,
                    new BigDecimal(variante.precio()), variante.comparacion() == null ? null : new BigDecimal(variante.comparacion()),
                    !demo.digital());
            jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                    + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, 0, ?, FALSE)", varianteId,
                    demo.digital() ? 0 : variante.stock(), !demo.digital());
            idsPorTitulo.put(variante.titulo(), varianteId);
        }
        List<FotoDemo> fotos = demo.fotos() != null ? demo.fotos() : fotosPorDefecto(demo, tienda);
        int posicion = 1;
        for (FotoDemo foto : fotos) {
            jdbc.update("INSERT INTO \"ImagenesProducto\" (\"ProductoId\", \"UrlImagen\", \"TextoAlternativo\", \"EsPrincipal\", \"Posicion\", "
                    + "\"VarianteId\") VALUES (?, ?, ?, ?, ?, ?)", productoId, foto.url(),
                    foto.variante() == null ? demo.titulo() : demo.titulo() + " (" + foto.variante() + ")", posicion == 1, posicion,
                    foto.variante() == null ? null : idsPorTitulo.get(foto.variante()));
            posicion++;
        }
        contador.creado("productos");
    }

    /** Variantes de un producto: las definidas una por una o, si no, las del título con precio y stock comunes. */
    private static List<VarianteDemo> variantesDe(ProductoDemo demo) {
        if (demo.detalle() != null) {
            return demo.detalle();
        }
        int stock = demo.digital() ? 0 : 60;
        List<String> titulos = demo.variantes() == null ? List.of("Por defecto") : demo.variantes();
        return titulos.stream().map(t -> new VarianteDemo(t, demo.precio(), demo.comparacion(), stock)).toList();
    }

    /** Dos imágenes que siempre cargan: un bloque de color con el nombre del producto (placehold.co). */
    private static List<FotoDemo> fotosPorDefecto(ProductoDemo demo, TiendaDemo tienda) {
        String[] fondos = {"6f4e37", "c8a96a", "8a5a44", "1f3a5f", "3f6b4f", "5b4b8a"};
        int indice = DatosDemo.TIENDAS.indexOf(tienda);
        String fondo = fondos[Math.floorMod(indice, fondos.length)];
        String texto = java.net.URLEncoder.encode(demo.titulo(), StandardCharsets.UTF_8);
        return List.of(
                new FotoDemo("https://placehold.co/800x800/" + fondo + "/ffffff/png?text=" + texto, null),
                new FotoDemo("https://placehold.co/800x800/f4efe6/" + fondo + "/png?text=" + texto, null));
    }

    private void tarifa(UUID tiendaId, String nombre, String precio, String gratisDesde, int orden, Contador contador) {
        boolean existe = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM \"TarifasEnvio\" WHERE \"TiendaId\" = ? "
                + "AND \"Nombre\" = ? AND \"Eliminado\" = FALSE)", Boolean.class, tiendaId, nombre));
        if (existe) {
            contador.omitido("tarifas");
            return;
        }
        jdbc.update("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"MontoMinimoGratis\", \"EstaActiva\", \"Orden\") "
                + "VALUES (?, ?, ?, ?, TRUE, ?)", tiendaId, nombre, new BigDecimal(precio),
                gratisDesde == null ? null : new BigDecimal(gratisDesde), orden);
        contador.creado("tarifas");
    }

    // ------------------------------------------------------------------ pedidos

    /** Pedidos del comprador demo (variados) y uno por persona y tienda con productos entregados, para las reseñas. */
    private Map<String, List<Tramo>> pedidosDeEjemplo() {
        Map<String, List<Tramo>> mapa = new LinkedHashMap<>();
        mapa.put("comprador-mixto", List.of(
                new Tramo("cafe-andino", List.of(new int[]{0, 2}), Estado.PAGADO),
                new Tramo("neo-gadgets", List.of(new int[]{3, 1}), Estado.PENDIENTE)));
        mapa.put("comprador-preparado", List.of(new Tramo("cafe-andino", List.of(new int[]{3, 1}), Estado.PREPARADO)));
        mapa.put("comprador-quinua", List.of(new Tramo("casa-quinua", List.of(new int[]{0, 2}, new int[]{1, 1}), Estado.ENTREGADO)));
        mapa.put("comprador-textil", List.of(new Tramo("taller-textil-cusco", List.of(new int[]{0, 1}, new int[]{2, 1}), Estado.PAGADO)));
        mapa.put("comprador-gadget", List.of(new Tramo("neo-gadgets", List.of(new int[]{1, 1}), Estado.ENTREGADO)));
        for (TiendaDemo tienda : DatosDemo.TIENDAS) {
            for (String quien : List.of("comprador", "admin", "cruzado")) {
                mapa.put("resena-" + quien + "-" + tienda.subdominio(), List.of(
                        new Tramo(tienda.subdominio(), List.of(new int[]{0, 1}, new int[]{1, 1}), Estado.ENTREGADO)));
            }
        }
        return mapa;
    }

    private UUID usuarioDe(String clave, Cuentas cuentas) {
        if (clave.startsWith("resena-admin-")) {
            return cuentas.admin();
        }
        if (clave.startsWith("resena-cruzado-")) {
            String subdominio = clave.substring("resena-cruzado-".length());
            int duenio = DatosDemo.TIENDAS.stream().filter(t -> t.subdominio().equals(subdominio)).findFirst().orElseThrow().propietario();
            return cuentas.propietario(duenio == 0 ? 1 : 0);
        }
        return cuentas.comprador();
    }

    private void pedido(String clave, UUID usuarioId, List<Tramo> tramos, Map<String, UUID> tiendaIds, Contador contador) {
        UUID claveIdempotencia = UUID.nameUUIDFromBytes(("otemu-demo-" + clave).getBytes(StandardCharsets.UTF_8));
        if (pedidos.buscarPorClave(claveIdempotencia).isPresent()) {
            contador.omitido("pedidos");
            return;
        }
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuarioId);
        String nombre = jdbc.queryForObject("SELECT \"Nombres\" || ' ' || \"Apellidos\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuarioId);
        Instant vence = reloj.instant().plus(Duration.ofDays(365));

        BigDecimal subtotal = Dinero.CERO;
        BigDecimal descuento = Dinero.CERO;
        BigDecimal envio = Dinero.CERO;
        BigDecimal impuestos = Dinero.CERO;
        BigDecimal total = Dinero.CERO;
        List<UUID> ordenIds = new ArrayList<>();
        List<Object[]> porPagar = new ArrayList<>();

        for (Tramo tramo : tramos) {
            UUID tiendaId = tiendaIds.get(tramo.subdominio());
            TiendaDemo tienda = DatosDemo.TIENDAS.stream().filter(t -> t.subdominio().equals(tramo.subdominio())).findFirst().orElseThrow();
            ConfiguracionVenta cfg = tiendas.configuracionVenta(tiendaId);
            List<NuevaLinea> lineas = new ArrayList<>();
            List<LineaCalculo> calculo = new ArrayList<>();
            List<LineaInventario> inv = new ArrayList<>();
            for (int[] par : tramo.productos()) {
                Map<String, Object> v = variante(tiendaId, tienda.productos().get(par[0]).slug());
                UUID varianteId = (UUID) v.get("Id");
                BigDecimal precio = (BigDecimal) v.get("Precio");
                boolean requiere = Boolean.TRUE.equals(v.get("RequiereEnvio"));
                lineas.add(new NuevaLinea(varianteId, (String) v.get("producto"), (String) v.get("Titulo"), (String) v.get("SKU"), par[1],
                        precio, Dinero.multiplicar(precio, par[1]), requiere));
                calculo.add(new LineaCalculo(precio, par[1], requiere));
                inv.add(new LineaInventario(varianteId, par[1]));
            }
            TotalesCalculados t = CalculadoraTotales.calcular(calculo, null, new EnvioCalculo(new BigDecimal("8.00"), new BigDecimal("150.00")),
                    new ImpuestoCalculo(cfg.tasaImpuesto(), Boolean.TRUE.equals(cfg.preciosIncluyenImpuesto())));
            String numero = tiendas.tomarNumeroOrden(tiendaId);
            UUID ordenId = ordenes.crear(new NuevaOrden(tiendaId, usuarioId, numero, correo, nombre, null, t.subtotal(), t.descuentoTotal(),
                    t.costoEnvio(), t.impuestosTotal(), t.total(), null, null, "Estándar", PasarelaPago.MANUAL, cfg.tasaImpuesto(),
                    Boolean.TRUE.equals(cfg.preciosIncluyenImpuesto()), claveIdempotencia, generadorToken.tokenUrl(), vence,
                    direccion(nombre), direccion(nombre), lineas)).id();
            inventario.reservar(tiendaId, inv, ordenId);
            ordenIds.add(ordenId);
            subtotal = subtotal.add(t.subtotal());
            descuento = descuento.add(t.descuentoTotal());
            envio = envio.add(t.costoEnvio());
            impuestos = impuestos.add(t.impuestosTotal());
            total = total.add(t.total());
            porPagar.add(new Object[]{tiendaId, ordenId, t.total(), inv, tramo.estado()});
        }

        UUID pedidoId = pedidos.crear(new NuevoPedido(usuarioId, correo, nombre, null, MONEDA, subtotal, descuento, envio, impuestos, total,
                PasarelaPago.MANUAL, claveIdempotencia, generadorToken.tokenUrl(), vence), ordenIds).id();
        for (Object[] orden : porPagar) {
            avanzar(pedidoId, (UUID) orden[0], (UUID) orden[1], (BigDecimal) orden[2], castInventario(orden[3]), (Estado) orden[4]);
        }
        contador.creado("pedidos");
    }

    /** Lleva la orden al estado pedido con los mismos pasos que la confirmación de un pago manual, sin eventos ni correos. */
    private void avanzar(UUID pedidoId, UUID tiendaId, UUID ordenId, BigDecimal total, List<LineaInventario> inv, Estado estado) {
        if (estado == Estado.PENDIENTE) {
            return;
        }
        jdbc.update("INSERT INTO \"Pagos\" (\"OrdenId\", \"PasarelaPago\", \"Monto\", \"Moneda\", \"Estado\", \"Tipo\", \"Referencia\") "
                + "VALUES (?, 'MANUAL', ?, ?, 'APROBADO', 'CARGO', ?)", ordenId, total, MONEDA, "DEMO-" + ordenId.toString().substring(0, 8));
        inventario.confirmarVenta(tiendaId, inv, ordenId);
        BigDecimal comision = suscripciones.vigente(tiendaId).map(v -> Dinero.porcentaje(total, v.porcentajeComision())).orElse(Dinero.CERO);
        ordenes.aplicarPagoConfirmado(ordenId, comision);
        balance.registrarVenta(tiendaId, total, comision);
        repartos.registrarVenta(pedidoId, ordenId, tiendaId, total, comision, MONEDA);
        if (estado == Estado.PREPARADO || estado == Estado.ENTREGADO) {
            ordenes.marcarPreparada(ordenId);
        }
        if (estado == Estado.ENTREGADO) {
            ordenes.marcarEntregada(ordenId);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LineaInventario> castInventario(Object valor) {
        return (List<LineaInventario>) valor;
    }

    private Map<String, Object> variante(UUID tiendaId, String slug) {
        return jdbc.queryForList("SELECT v.\"Id\", v.\"Titulo\", v.\"SKU\", v.\"Precio\", v.\"RequiereEnvio\", p.\"Titulo\" AS producto "
                + "FROM \"VariantesProducto\" v JOIN \"Productos\" p ON p.\"Id\" = v.\"ProductoId\" "
                + "WHERE p.\"TiendaId\" = ? AND p.\"IdentificadorUrl\" = ? AND v.\"Eliminado\" = FALSE "
                + "ORDER BY v.\"Precio\", v.\"FechaCreacion\", v.\"Id\" LIMIT 1", tiendaId, slug).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("La semilla no encontró el producto " + slug));
    }

    private static Map<String, Object> direccion(String nombre) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("nombre_contacto", nombre);
        d.put("direccion_linea1", "Av. Demostración 123");
        d.put("ciudad", "Cusco");
        d.put("estado_provincia", "Cusco");
        d.put("codigo_postal", "08000");
        d.put("pais", "PE");
        d.put("latitud", new BigDecimal("-13.5320"));
        d.put("longitud", new BigDecimal("-71.9675"));
        return d;
    }

    // ------------------------------------------------------------------ reseñas

    /** Tres reseñas (comprador, administrador y el otro propietario) por cada uno de los dos primeros productos de cada tienda. */
    private void resenas(Cuentas cuentas, Map<String, UUID> tiendaIds, Contador contador) {
        int tiendaIdx = 0;
        for (TiendaDemo tienda : DatosDemo.TIENDAS) {
            UUID tiendaId = tiendaIds.get(tienda.subdominio());
            List<UUID> autores = List.of(cuentas.comprador(), cuentas.admin(), cuentas.propietario(tienda.propietario() == 0 ? 1 : 0));
            for (int p = 0; p < 2; p++) {
                UUID productoId = jdbc.queryForObject("SELECT \"Id\" FROM \"Productos\" WHERE \"TiendaId\" = ? AND \"IdentificadorUrl\" = ? "
                        + "AND \"Eliminado\" = FALSE", UUID.class, tiendaId, tienda.productos().get(p).slug());
                for (int a = 0; a < autores.size(); a++) {
                    int calificacion = 3 + ((tiendaIdx + p + a) % 3);
                    String comentario = DatosDemo.COMENTARIOS.get((tiendaIdx * 3 + p * 2 + a) % DatosDemo.COMENTARIOS.size());
                    int filas = jdbc.update("INSERT INTO \"ResenasProducto\" (\"ProductoId\", \"TiendaId\", \"UsuarioId\", \"Calificacion\", \"Comentario\") "
                            + "SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM \"ResenasProducto\" WHERE \"ProductoId\" = ? "
                            + "AND \"UsuarioId\" = ? AND \"Eliminado\" = FALSE)", productoId, tiendaId, autores.get(a), calificacion, comentario,
                            productoId, autores.get(a));
                    if (filas > 0) {
                        contador.creado("resenas");
                    } else {
                        contador.omitido("resenas");
                    }
                }
            }
            tiendaIdx++;
        }
    }
}
