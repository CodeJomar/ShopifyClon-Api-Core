-- =================================================================================
-- PROYECTO: CLON SHOPIFY (SUPABASE / POSTGRESQL) - ARQUITECTURA PRODUCCIÓN
-- ESTÁNDAR: UUID universal, Auditoría completa y Eliminación Lógica en todas las tablas
-- NOMENCLATURA: Español estricto, PascalCase entrecomillado ("Tabla", "Columna")
-- PATRÓN DDL: Definición de Tablas -> Llaves Primarias -> Restricciones -> Llaves Foráneas -> Índices
-- =================================================================================

-- =================================================================================
-- SECCIÓN 1: CREACIÓN DE ESTRUCTURAS DE TABLA
-- =================================================================================

-- 1. IDENTIDAD, TIENDAS Y ROLES
CREATE TABLE "Usuarios" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "CorreoElectronico" VARCHAR(255) NOT NULL,
    "ContrasenaHash" VARCHAR(255) NOT NULL,
    "Nombres" VARCHAR(100) NOT NULL,
    "Apellidos" VARCHAR(100) NOT NULL,
    "Telefono" VARCHAR(30),
    "EstaActivo" BOOLEAN DEFAULT TRUE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "Roles" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "Nombre" VARCHAR(50) NOT NULL,
    "Codigo" VARCHAR(30) NOT NULL,
    "Descripcion" VARCHAR(255),
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "Tiendas" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "PropietarioId" UUID NOT NULL,
    "Nombre" VARCHAR(100) NOT NULL,
    "Subdominio" VARCHAR(100) NOT NULL,
    "DominioPersonalizado" VARCHAR(150),
    "Moneda" VARCHAR(3) DEFAULT 'USD' NOT NULL,
    "CorreoContacto" VARCHAR(255) NOT NULL,
    "EstaActiva" BOOLEAN DEFAULT TRUE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "PersonalTienda" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "UsuarioId" UUID NOT NULL,
    "RolId" UUID NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "DireccionesUsuario" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "UsuarioId" UUID NOT NULL,
    "NombreContacto" VARCHAR(150) NOT NULL,
    "Telefono" VARCHAR(30),
    "DireccionLinea1" VARCHAR(255) NOT NULL,
    "DireccionLinea2" VARCHAR(255),
    "Ciudad" VARCHAR(100) NOT NULL,
    "EstadoProvincia" VARCHAR(100) NOT NULL,
    "CodigoPostal" VARCHAR(20) NOT NULL,
    "Pais" VARCHAR(100) NOT NULL,
    "EsPrincipal" BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 2. CATÁLOGO, PRODUCTOS Y VARIANTES
CREATE TABLE "Categorias" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "Nombre" VARCHAR(100) NOT NULL,
    "IdentificadorUrl" VARCHAR(100) NOT NULL,
    "Descripcion" TEXT,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "Productos" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "Titulo" VARCHAR(200) NOT NULL,
    "IdentificadorUrl" VARCHAR(200) NOT NULL,
    "Descripcion" TEXT,
    "Proveedor" VARCHAR(100),
    "EsDigital" BOOLEAN DEFAULT FALSE NOT NULL,
    "EstaPublicado" BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "ProductosCategorias" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "ProductoId" UUID NOT NULL,
    "CategoriaId" UUID NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "VariantesProducto" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "ProductoId" UUID NOT NULL,
    "Titulo" VARCHAR(150) NOT NULL, -- ej: "Talla M / Negro" o "Por Defecto"
    "SKU" VARCHAR(80) NOT NULL,
    "CodigoBarras" VARCHAR(50),
    "Precio" DECIMAL(12, 2) NOT NULL,
    "PrecioComparacion" DECIMAL(12, 2), -- Precio tachado / oferta
    "CostoUnitario" DECIMAL(12, 2),    -- Margen administrativo
    "PesoGramos" DECIMAL(10, 2) DEFAULT 0.00,
    "RequiereEnvio" BOOLEAN DEFAULT TRUE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "InventarioVariante" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "VarianteId" UUID NOT NULL,
    "CantidadDisponible" INTEGER DEFAULT 0 NOT NULL,
    "CantidadReservada" INTEGER DEFAULT 0 NOT NULL, -- Apartado durante checkout
    "RastreaInventario" BOOLEAN DEFAULT TRUE NOT NULL,
    "PermitirVentaSinStock" BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "ImagenesProducto" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "ProductoId" UUID NOT NULL,
    "VarianteId" UUID, -- Opcional: si la foto corresponde a un color específico
    "UrlImagen" TEXT NOT NULL,
    "TextoAlternativo" VARCHAR(200),
    "Posicion" INTEGER DEFAULT 1 NOT NULL,
    "EsPrincipal" BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 3. MARKETING Y DESCUENTOS
CREATE TABLE "Cupones" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "Codigo" VARCHAR(50) NOT NULL,
    "TipoDescuento" VARCHAR(20) NOT NULL, -- 'PORCENTAJE', 'MONTO_FIJO', 'ENVIO_GRATIS'
    "ValorDescuento" DECIMAL(12, 2) NOT NULL,
    "MontoMinimoCompra" DECIMAL(12, 2) DEFAULT 0.00,
    "LimiteUsosTotal" INTEGER,
    "CantidadUsosActual" INTEGER DEFAULT 0 NOT NULL,
    "FechaInicio" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "FechaFin" TIMESTAMP WITH TIME ZONE,
    "EstaActivo" BOOLEAN DEFAULT TRUE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 4. CARRITOS DE COMPRA (CON SOPORTE INVITADOS)
CREATE TABLE "Carritos" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "UsuarioId" UUID, -- Opcional: permite compradores sin cuenta previa
    "TokenSesion" VARCHAR(100) NOT NULL, -- Cookie / LocalStorage del navegador
    "CuponId" UUID,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "ItemsCarrito" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "CarritoId" UUID NOT NULL,
    "VarianteId" UUID NOT NULL, -- Apunta a la variante exacta
    "Cantidad" INTEGER NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 5. ÓRDENES, PAGOS Y ENVÍOS
CREATE TABLE "Ordenes" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "UsuarioId" UUID, -- Opcional si compró como invitado
    "NumeroOrden" VARCHAR(30) NOT NULL, -- ej: "#1001"
    "CorreoCliente" VARCHAR(255) NOT NULL,
    "NombreCliente" VARCHAR(150) NOT NULL,
    "TelefonoCliente" VARCHAR(30),
    
    -- Desglose financiero
    "Subtotal" DECIMAL(12, 2) NOT NULL,
    "DescuentoTotal" DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    "CostoEnvio" DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    "ImpuestosTotal" DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    "Total" DECIMAL(12, 2) NOT NULL,
    
    -- Estados operativos independientes
    "EstadoPago" VARCHAR(30) DEFAULT 'PENDIENTE' NOT NULL, -- 'PENDIENTE', 'PAGADO', 'REEMBOLSADO'
    "EstadoPreparacion" VARCHAR(30) DEFAULT 'NO_PREPARADO' NOT NULL, -- 'NO_PREPARADO', 'PREPARADO', 'ENTREGADO'
    
    -- Snapshot de direcciones para congelar historial
    "DireccionEnvio" JSONB,
    "DireccionFacturacion" JSONB,
    "CuponId" UUID,
    
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "ItemsOrden" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "OrdenId" UUID NOT NULL,
    "VarianteId" UUID NOT NULL,
    "TituloProducto" VARCHAR(200) NOT NULL, -- Snapshot en caso de que se borre el producto
    "TituloVariante" VARCHAR(150) NOT NULL,
    "SKU" VARCHAR(80),
    "Cantidad" INTEGER NOT NULL,
    "PrecioUnitario" DECIMAL(12, 2) NOT NULL,
    "TotalLinea" DECIMAL(12, 2) NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "Pagos" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "OrdenId" UUID NOT NULL,
    "PasarelaPago" VARCHAR(50) NOT NULL, -- 'STRIPE', 'PAYPAL', 'MERCADOPAGO', 'EFECTIVO'
    "TransaccionId" VARCHAR(255),
    "Monto" DECIMAL(12, 2) NOT NULL,
    "Moneda" VARCHAR(3) DEFAULT 'USD' NOT NULL,
    "Estado" VARCHAR(30) NOT NULL, -- 'APROBADO', 'PENDIENTE', 'RECHAZADO'
    "DetallesRespuesta" JSONB,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "Envios" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "OrdenId" UUID NOT NULL,
    "EmpresaTransporte" VARCHAR(100), -- 'DHL', 'FedEx', 'Coordinadora', etc.
    "NumeroGuia" VARCHAR(100),
    "UrlSeguimiento" TEXT,
    "Estado" VARCHAR(50) DEFAULT 'CREADO' NOT NULL, -- 'CREADO', 'EN_TRANSITO', 'ENTREGADO'
    "FechaDespacho" TIMESTAMP WITH TIME ZONE,
    "FechaEntregaEstimada" TIMESTAMP WITH TIME ZONE,
    "FechaEntregaReal" TIMESTAMP WITH TIME ZONE,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE "BalancesTienda" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "TotalVentasHistorico" DECIMAL(14, 2) DEFAULT 0.00 NOT NULL,
    "CantidadOrdenes" INTEGER DEFAULT 0 NOT NULL,
    "SaldoPendienteCobro" DECIMAL(14, 2) DEFAULT 0.00 NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- =================================================================================
-- SECCIÓN 2: ASIGNACIÓN DE LLAVES PRIMARIAS (UUID)
-- =================================================================================

ALTER TABLE "Usuarios" ADD CONSTRAINT "PkUsuarios" PRIMARY KEY ("Id");
ALTER TABLE "Roles" ADD CONSTRAINT "PkRoles" PRIMARY KEY ("Id");
ALTER TABLE "Tiendas" ADD CONSTRAINT "PkTiendas" PRIMARY KEY ("Id");
ALTER TABLE "PersonalTienda" ADD CONSTRAINT "PkPersonalTienda" PRIMARY KEY ("Id");
ALTER TABLE "DireccionesUsuario" ADD CONSTRAINT "PkDireccionesUsuario" PRIMARY KEY ("Id");
ALTER TABLE "Categorias" ADD CONSTRAINT "PkCategorias" PRIMARY KEY ("Id");
ALTER TABLE "Productos" ADD CONSTRAINT "PkProductos" PRIMARY KEY ("Id");
ALTER TABLE "ProductosCategorias" ADD CONSTRAINT "PkProductosCategorias" PRIMARY KEY ("Id");
ALTER TABLE "VariantesProducto" ADD CONSTRAINT "PkVariantesProducto" PRIMARY KEY ("Id");
ALTER TABLE "InventarioVariante" ADD CONSTRAINT "PkInventarioVariante" PRIMARY KEY ("Id");
ALTER TABLE "ImagenesProducto" ADD CONSTRAINT "PkImagenesProducto" PRIMARY KEY ("Id");
ALTER TABLE "Cupones" ADD CONSTRAINT "PkCupones" PRIMARY KEY ("Id");
ALTER TABLE "Carritos" ADD CONSTRAINT "PkCarritos" PRIMARY KEY ("Id");
ALTER TABLE "ItemsCarrito" ADD CONSTRAINT "PkItemsCarrito" PRIMARY KEY ("Id");
ALTER TABLE "Ordenes" ADD CONSTRAINT "PkOrdenes" PRIMARY KEY ("Id");
ALTER TABLE "ItemsOrden" ADD CONSTRAINT "PkItemsOrden" PRIMARY KEY ("Id");
ALTER TABLE "Pagos" ADD CONSTRAINT "PkPagos" PRIMARY KEY ("Id");
ALTER TABLE "Envios" ADD CONSTRAINT "PkEnvios" PRIMARY KEY ("Id");
ALTER TABLE "BalancesTienda" ADD CONSTRAINT "PkBalancesTienda" PRIMARY KEY ("Id");

-- =================================================================================
-- SECCIÓN 3: RESTRICCIONES DE UNICIDAD Y CHECKS
-- =================================================================================

ALTER TABLE "Usuarios" 
    ADD CONSTRAINT "UqUsuariosCorreoElectronico" UNIQUE ("CorreoElectronico");

ALTER TABLE "Roles" 
    ADD CONSTRAINT "UqRolesCodigo" UNIQUE ("Codigo");

ALTER TABLE "Tiendas" 
    ADD CONSTRAINT "UqTiendasSubdominio" UNIQUE ("Subdominio");

ALTER TABLE "PersonalTienda" 
    ADD CONSTRAINT "UqPersonalTiendaUsuarioRol" UNIQUE ("TiendaId", "UsuarioId", "RolId");

ALTER TABLE "Categorias" 
    ADD CONSTRAINT "UqCategoriasTiendaIdentificadorUrl" UNIQUE ("TiendaId", "IdentificadorUrl");

ALTER TABLE "Productos" 
    ADD CONSTRAINT "UqProductosTiendaIdentificadorUrl" UNIQUE ("TiendaId", "IdentificadorUrl");

ALTER TABLE "ProductosCategorias" 
    ADD CONSTRAINT "UqProductosCategoriasRelacion" UNIQUE ("ProductoId", "CategoriaId");

ALTER TABLE "VariantesProducto" 
    ADD CONSTRAINT "UqVariantesProductoSKU" UNIQUE ("SKU");

ALTER TABLE "InventarioVariante" 
    ADD CONSTRAINT "UqInventarioVariante" UNIQUE ("VarianteId");

ALTER TABLE "Cupones" 
    ADD CONSTRAINT "UqCuponesTiendaCodigo" UNIQUE ("TiendaId", "Codigo");

ALTER TABLE "Carritos" 
    ADD CONSTRAINT "UqCarritosTiendaToken" UNIQUE ("TiendaId", "TokenSesion");

ALTER TABLE "ItemsCarrito" 
    ADD CONSTRAINT "UqItemsCarritoVariante" UNIQUE ("CarritoId", "VarianteId");

ALTER TABLE "Ordenes" 
    ADD CONSTRAINT "UqOrdenesTiendaNumero" UNIQUE ("TiendaId", "NumeroOrden");

ALTER TABLE "BalancesTienda" 
    ADD CONSTRAINT "UqBalancesTiendaTienda" UNIQUE ("TiendaId");

-- Validaciones lógicas numéricas
ALTER TABLE "VariantesProducto" 
    ADD CONSTRAINT "ChkVariantesPrecio" CHECK ("Precio" >= 0),
    ADD CONSTRAINT "ChkVariantesPrecioComparacion" CHECK ("PrecioComparacion" IS NULL OR "PrecioComparacion" >= 0);

ALTER TABLE "InventarioVariante" 
    ADD CONSTRAINT "ChkInventarioDisponible" CHECK ("CantidadDisponible" >= 0 OR "PermitirVentaSinStock" = TRUE),
    ADD CONSTRAINT "ChkInventarioReservado" CHECK ("CantidadReservada" >= 0);

ALTER TABLE "ItemsCarrito" 
    ADD CONSTRAINT "ChkItemsCarritoCantidad" CHECK ("Cantidad" > 0);

ALTER TABLE "Ordenes" 
    ADD CONSTRAINT "ChkOrdenesTotal" CHECK ("Total" >= 0),
    ADD CONSTRAINT "ChkOrdenesSubtotal" CHECK ("Subtotal" >= 0);

ALTER TABLE "ItemsOrden" 
    ADD CONSTRAINT "ChkItemsOrdenCantidad" CHECK ("Cantidad" > 0),
    ADD CONSTRAINT "ChkItemsOrdenPrecioUnitario" CHECK ("PrecioUnitario" >= 0);

-- =================================================================================
-- SECCIÓN 4: ASIGNACIÓN DE LLAVES FORÁNEAS (RELACIONALES)
-- =================================================================================

-- Tiendas y Personal
ALTER TABLE "Tiendas"
    ADD CONSTRAINT "FkTiendasPropietario" FOREIGN KEY ("PropietarioId") REFERENCES "Usuarios"("Id") ON DELETE RESTRICT;

ALTER TABLE "PersonalTienda"
    ADD CONSTRAINT "FkPersonalTiendaTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkPersonalTiendaUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkPersonalTiendaRol" FOREIGN KEY ("RolId") REFERENCES "Roles"("Id") ON DELETE RESTRICT;

ALTER TABLE "DireccionesUsuario"
    ADD CONSTRAINT "FkDireccionesUsuarioUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE CASCADE;

-- Catálogo
ALTER TABLE "Categorias"
    ADD CONSTRAINT "FkCategoriasTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE;

ALTER TABLE "Productos"
    ADD CONSTRAINT "FkProductosTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE;

ALTER TABLE "ProductosCategorias"
    ADD CONSTRAINT "FkProductosCategoriasProducto" FOREIGN KEY ("ProductoId") REFERENCES "Productos"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkProductosCategoriasCategoria" FOREIGN KEY ("CategoriaId") REFERENCES "Categorias"("Id") ON DELETE CASCADE;

ALTER TABLE "VariantesProducto"
    ADD CONSTRAINT "FkVariantesProductoProducto" FOREIGN KEY ("ProductoId") REFERENCES "Productos"("Id") ON DELETE CASCADE;

ALTER TABLE "InventarioVariante"
    ADD CONSTRAINT "FkInventarioVarianteVariante" FOREIGN KEY ("VarianteId") REFERENCES "VariantesProducto"("Id") ON DELETE CASCADE;

ALTER TABLE "ImagenesProducto"
    ADD CONSTRAINT "FkImagenesProductoProducto" FOREIGN KEY ("ProductoId") REFERENCES "Productos"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkImagenesProductoVariante" FOREIGN KEY ("VarianteId") REFERENCES "VariantesProducto"("Id") ON DELETE SET NULL;

-- Marketing
ALTER TABLE "Cupones"
    ADD CONSTRAINT "FkCuponesTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE;

-- Carritos
ALTER TABLE "Carritos"
    ADD CONSTRAINT "FkCarritosTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkCarritosUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkCarritosCupon" FOREIGN KEY ("CuponId") REFERENCES "Cupones"("Id") ON DELETE SET NULL;

ALTER TABLE "ItemsCarrito"
    ADD CONSTRAINT "FkItemsCarritoCarrito" FOREIGN KEY ("CarritoId") REFERENCES "Carritos"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkItemsCarritoVariante" FOREIGN KEY ("VarianteId") REFERENCES "VariantesProducto"("Id") ON DELETE RESTRICT;

-- Órdenes y Transacciones
ALTER TABLE "Ordenes"
    ADD CONSTRAINT "FkOrdenesTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE RESTRICT,
    ADD CONSTRAINT "FkOrdenesUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkOrdenesCupon" FOREIGN KEY ("CuponId") REFERENCES "Cupones"("Id") ON DELETE SET NULL;

ALTER TABLE "ItemsOrden"
    ADD CONSTRAINT "FkItemsOrdenOrden" FOREIGN KEY ("OrdenId") REFERENCES "Ordenes"("Id") ON DELETE CASCADE,
    ADD CONSTRAINT "FkItemsOrdenVariante" FOREIGN KEY ("VarianteId") REFERENCES "VariantesProducto"("Id") ON DELETE RESTRICT;

ALTER TABLE "Pagos"
    ADD CONSTRAINT "FkPagosOrden" FOREIGN KEY ("OrdenId") REFERENCES "Ordenes"("Id") ON DELETE RESTRICT;

ALTER TABLE "Envios"
    ADD CONSTRAINT "FkEnviosOrden" FOREIGN KEY ("OrdenId") REFERENCES "Ordenes"("Id") ON DELETE CASCADE;

ALTER TABLE "BalancesTienda"
    ADD CONSTRAINT "FkBalancesTiendaTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE;

-- =================================================================================
-- SECCIÓN 5: INTEGRIDAD DE AUDITORÍA (TRAZABILIDAD)
-- =================================================================================

ALTER TABLE "Usuarios"
    ADD CONSTRAINT "FkUsuariosUsuarioCreacion" FOREIGN KEY ("UsuarioCreacion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkUsuariosUsuarioEdicion" FOREIGN KEY ("UsuarioEdicion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL;

ALTER TABLE "Tiendas"
    ADD CONSTRAINT "FkTiendasUsuarioCreacion" FOREIGN KEY ("UsuarioCreacion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkTiendasUsuarioEdicion" FOREIGN KEY ("UsuarioEdicion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL;

ALTER TABLE "Productos"
    ADD CONSTRAINT "FkProductosUsuarioCreacion" FOREIGN KEY ("UsuarioCreacion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkProductosUsuarioEdicion" FOREIGN KEY ("UsuarioEdicion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL;

ALTER TABLE "VariantesProducto"
    ADD CONSTRAINT "FkVariantesProductoUsuarioCreacion" FOREIGN KEY ("UsuarioCreacion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkVariantesProductoUsuarioEdicion" FOREIGN KEY ("UsuarioEdicion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL;

ALTER TABLE "Ordenes"
    ADD CONSTRAINT "FkOrdenesUsuarioCreacion" FOREIGN KEY ("UsuarioCreacion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    ADD CONSTRAINT "FkOrdenesUsuarioEdicion" FOREIGN KEY ("UsuarioEdicion") REFERENCES "Usuarios"("Id") ON DELETE SET NULL;

-- =================================================================================
-- SECCIÓN 6: ÍNDICES DE RENDIMIENTO (OPTIMIZADOS PARA SOFT DELETE)
-- =================================================================================

-- Autenticación y Tiendas
CREATE INDEX "IdxUsuariosActivos" ON "Usuarios"("CorreoElectronico") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxTiendasSubdominioActivo" ON "Tiendas"("Subdominio") WHERE "Eliminado" = FALSE;

-- Catálogo y Navegación
CREATE INDEX "IdxProductosTiendaActivos" ON "Productos"("TiendaId", "EstaPublicado") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxVariantesProducto" ON "VariantesProducto"("ProductoId", "SKU") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxImagenesProducto" ON "ImagenesProducto"("ProductoId", "Posicion") WHERE "Eliminado" = FALSE;

-- Carrito y Checkout
CREATE INDEX "IdxCarritosToken" ON "Carritos"("TiendaId", "TokenSesion") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxItemsCarritoCarrito" ON "ItemsCarrito"("CarritoId") WHERE "Eliminado" = FALSE;

-- Operaciones y Despacho
CREATE INDEX "IdxOrdenesTiendaEstado" ON "Ordenes"("TiendaId", "EstadoPago", "EstadoPreparacion") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxItemsOrdenOrden" ON "ItemsOrden"("OrdenId") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxEnviosOrden" ON "Envios"("OrdenId") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxPagosOrden" ON "Pagos"("OrdenId") WHERE "Eliminado" = FALSE;

CREATE TABLE "TokensSeguridad" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "UsuarioId" UUID NOT NULL,
    "CodigoToken" VARCHAR(100) NOT NULL,
    "Tipo" VARCHAR(30) NOT NULL, -- 'ACTIVACION_CUENTA', 'RECUPERACION_CLAVE'
    "FechaExpiracion" TIMESTAMP WITH TIME ZONE NOT NULL,
    "Usado" BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkTokensSeguridad" PRIMARY KEY ("Id"),
    CONSTRAINT "FkTokensSeguridadUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE CASCADE
);

CREATE INDEX "IdxTokensSeguridadValidacion" 
ON "TokensSeguridad"("CodigoToken", "Tipo", "Usado", "FechaExpiracion") 
WHERE "Eliminado" = FALSE;