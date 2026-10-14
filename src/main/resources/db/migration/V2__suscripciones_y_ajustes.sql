-- =================================================================================
-- MIGRACIÓN V002: PLANES DE SUSCRIPCIÓN + AJUSTES DE INTEGRIDAD
-- Base: database.sql (V001). Ejecutar completa dentro de una transacción.
-- Reglas: docs/negocio (ver docs/base-de-datos/analisis.md para el porqué de cada cambio)
-- Convención: Pk / Uq / Ux (único parcial) / Chk / Fk / Idx, PascalCase entrecomillado.
-- =================================================================================

-- =================================================================================
-- SECCIÓN 1: TABLAS NUEVAS
-- =================================================================================

-- 1.1 Catálogo de planes de la plataforma (D-002)
CREATE TABLE "PlanesSuscripcion" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "Codigo" VARCHAR(30) NOT NULL,                  -- 'BASICO', 'ESTANDAR', 'AVANZADO'
    "Nombre" VARCHAR(100) NOT NULL,
    "Descripcion" VARCHAR(500),
    "PrecioMensual" DECIMAL(12, 2) NOT NULL,
    "PrecioAnual" DECIMAL(12, 2) NOT NULL,
    "Moneda" VARCHAR(3) DEFAULT 'USD' NOT NULL,
    "LimiteProductos" INTEGER,                       -- NULL = ilimitado
    "LimitePersonal" INTEGER NOT NULL,               -- sin contar al propietario
    "PorcentajeComision" DECIMAL(5, 4) NOT NULL,     -- 0.0200 = 2 %
    "DiasPrueba" INTEGER DEFAULT 14 NOT NULL,
    "EsPublico" BOOLEAN DEFAULT TRUE NOT NULL,       -- FALSE = no se ofrece a tiendas nuevas
    "Orden" INTEGER DEFAULT 0 NOT NULL,              -- orden de presentación
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 1.2 Contrato vigente tienda ↔ plan
CREATE TABLE "SuscripcionesTienda" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "PlanId" UUID NOT NULL,
    "Estado" VARCHAR(20) NOT NULL,                   -- 'PRUEBA','ACTIVA','VENCIDA','CANCELADA','SUSPENDIDA'
    "EstadoAnterior" VARCHAR(20),                    -- para reactivar una SUSPENDIDA (SUS-010)
    "CicloFacturacion" VARCHAR(10) DEFAULT 'MENSUAL' NOT NULL, -- 'MENSUAL','ANUAL'
    "PrecioPactado" DECIMAL(12, 2) NOT NULL,         -- congelado por período (SUS-003)
    "Moneda" VARCHAR(3) DEFAULT 'USD' NOT NULL,
    "FechaInicio" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "FechaFinPrueba" TIMESTAMP WITH TIME ZONE,
    "InicioPeriodoActual" TIMESTAMP WITH TIME ZONE NOT NULL,
    "FinPeriodoActual" TIMESTAMP WITH TIME ZONE NOT NULL,
    "FechaVencimiento" TIMESTAMP WITH TIME ZONE,     -- cuándo pasó a VENCIDA (SUS-007)
    "CancelarAlFinalizarPeriodo" BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCancelacion" TIMESTAMP WITH TIME ZONE,
    "MotivoEstado" VARCHAR(255),                     -- motivo de suspensión / cancelación
    "ReferenciaPasarela" VARCHAR(255),               -- id de cliente/suscripción en la pasarela
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 1.3 Intentos de cobro de cada período (solo-añadir)
CREATE TABLE "CobrosSuscripcion" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "SuscripcionId" UUID NOT NULL,
    "PlanId" UUID NOT NULL,                          -- plan cobrado (puede diferir tras cambio)
    "Monto" DECIMAL(12, 2) NOT NULL,
    "Moneda" VARCHAR(3) DEFAULT 'USD' NOT NULL,
    "InicioPeriodo" TIMESTAMP WITH TIME ZONE NOT NULL,
    "FinPeriodo" TIMESTAMP WITH TIME ZONE NOT NULL,
    "Estado" VARCHAR(20) NOT NULL,                   -- 'PENDIENTE','APROBADO','RECHAZADO'
    "PasarelaPago" VARCHAR(50) NOT NULL,             -- 'SIMULADA','STRIPE'
    "TransaccionId" VARCHAR(255),
    "Intento" INTEGER DEFAULT 1 NOT NULL,
    "DetallesRespuesta" JSONB,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 1.4 Tarifas de envío por tienda (D-011)
CREATE TABLE "TarifasEnvio" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "Nombre" VARCHAR(100) NOT NULL,
    "Descripcion" VARCHAR(255),
    "Precio" DECIMAL(12, 2) NOT NULL,
    "MontoMinimoGratis" DECIMAL(12, 2),             -- NULL = nunca gratis por monto
    "EstaActiva" BOOLEAN DEFAULT TRUE NOT NULL,
    "Orden" INTEGER DEFAULT 0 NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 1.5 Libro de movimientos de inventario (solo-añadir, INV-008)
CREATE TABLE "MovimientosInventario" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "VarianteId" UUID NOT NULL,
    "Tipo" VARCHAR(20) NOT NULL,                     -- 'INICIAL','AJUSTE','RESERVA','LIBERACION','VENTA','REPOSICION'
    "DeltaDisponible" INTEGER DEFAULT 0 NOT NULL,
    "DeltaReservado" INTEGER DEFAULT 0 NOT NULL,
    "DisponibleResultante" INTEGER NOT NULL,
    "ReservadoResultante" INTEGER NOT NULL,
    "OrdenId" UUID,
    "Motivo" VARCHAR(255),
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

-- 1.6 Registro de auditoría (solo-añadir, AUD-001)
CREATE TABLE "RegistroAuditoria" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID,                                 -- NULL en acciones de plataforma
    "UsuarioId" UUID,                                -- NULL si la acción es del sistema
    "Accion" VARCHAR(60) NOT NULL,                   -- 'ORDEN_CANCELADA','INVENTARIO_AJUSTADO',...
    "Entidad" VARCHAR(60) NOT NULL,                  -- 'Ordenes','InventarioVariante',...
    "EntidadId" UUID,
    "Motivo" VARCHAR(255),
    "Detalle" JSONB,                                 -- { "antes": {...}, "despues": {...} }
    "IdCorrelacion" VARCHAR(64),
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL
);

ALTER TABLE "PlanesSuscripcion"     ADD CONSTRAINT "PkPlanesSuscripcion"     PRIMARY KEY ("Id");
ALTER TABLE "SuscripcionesTienda"   ADD CONSTRAINT "PkSuscripcionesTienda"   PRIMARY KEY ("Id");
ALTER TABLE "CobrosSuscripcion"     ADD CONSTRAINT "PkCobrosSuscripcion"     PRIMARY KEY ("Id");
ALTER TABLE "TarifasEnvio"          ADD CONSTRAINT "PkTarifasEnvio"          PRIMARY KEY ("Id");
ALTER TABLE "MovimientosInventario" ADD CONSTRAINT "PkMovimientosInventario" PRIMARY KEY ("Id");
ALTER TABLE "RegistroAuditoria"     ADD CONSTRAINT "PkRegistroAuditoria"     PRIMARY KEY ("Id");

-- =================================================================================
-- SECCIÓN 2: COLUMNAS NUEVAS EN TABLAS EXISTENTES
-- =================================================================================

ALTER TABLE "Usuarios"
    ADD COLUMN "EsAdministradorPlataforma" BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE "Tiendas"
    ADD COLUMN "TasaImpuesto" DECIMAL(5, 4) DEFAULT 0.1800 NOT NULL,
    ADD COLUMN "PreciosIncluyenImpuesto" BOOLEAN DEFAULT TRUE NOT NULL,
    ADD COLUMN "ZonaHoraria" VARCHAR(50) DEFAULT 'America/Lima' NOT NULL,
    ADD COLUMN "SiguienteNumeroOrden" INTEGER DEFAULT 1001 NOT NULL;

ALTER TABLE "Carritos"
    ADD COLUMN "Estado" VARCHAR(20) DEFAULT 'ACTIVO' NOT NULL,     -- 'ACTIVO','CONVERTIDO'
    ADD COLUMN "OrdenId" UUID;

ALTER TABLE "Ordenes"
    ADD COLUMN "Estado" VARCHAR(20) DEFAULT 'ABIERTA' NOT NULL,    -- 'ABIERTA','CERRADA','CANCELADA'
    ADD COLUMN "PasarelaPago" VARCHAR(50),                         -- elegida en el checkout
    ADD COLUMN "CodigoCupon" VARCHAR(50),                          -- instantánea
    ADD COLUMN "NombreTarifaEnvio" VARCHAR(100),                   -- instantánea
    ADD COLUMN "TasaImpuesto" DECIMAL(5, 4) DEFAULT 0 NOT NULL,    -- instantánea
    ADD COLUMN "PreciosIncluyenImpuesto" BOOLEAN DEFAULT TRUE NOT NULL, -- instantánea
    ADD COLUMN "ComisionPlataforma" DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    ADD COLUMN "ClaveIdempotencia" UUID,
    ADD COLUMN "TokenAcceso" VARCHAR(100),
    ADD COLUMN "FechaExpiracionPago" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN "FechaCancelacion" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN "MotivoCancelacion" VARCHAR(30),                    -- 'CLIENTE','FRAUDE','SIN_STOCK','PAGO_VENCIDO','OTRO'
    ADD COLUMN "DetalleCancelacion" VARCHAR(255),
    ADD COLUMN "FechaCierre" TIMESTAMP WITH TIME ZONE;

ALTER TABLE "ItemsOrden"
    ADD COLUMN "RequiereEnvio" BOOLEAN DEFAULT TRUE NOT NULL;

ALTER TABLE "Pagos"
    ADD COLUMN "Tipo" VARCHAR(20) DEFAULT 'CARGO' NOT NULL,        -- 'CARGO','REEMBOLSO'
    ADD COLUMN "PagoOrigenId" UUID,                                -- cargo que se reembolsa
    ADD COLUMN "Referencia" VARCHAR(100),                          -- nro. de operación (pago manual)
    ADD COLUMN "Motivo" VARCHAR(255);

ALTER TABLE "BalancesTienda"
    ADD COLUMN "TotalReembolsado" DECIMAL(14, 2) DEFAULT 0.00 NOT NULL,
    ADD COLUMN "TotalComisiones" DECIMAL(14, 2) DEFAULT 0.00 NOT NULL;

-- =================================================================================
-- SECCIÓN 3: UNICIDADES (reemplazo por índices parciales, D-013 / D-014)
-- =================================================================================

ALTER TABLE "PersonalTienda"      DROP CONSTRAINT "UqPersonalTiendaUsuarioRol";
ALTER TABLE "Categorias"          DROP CONSTRAINT "UqCategoriasTiendaIdentificadorUrl";
ALTER TABLE "Productos"           DROP CONSTRAINT "UqProductosTiendaIdentificadorUrl";
ALTER TABLE "ProductosCategorias" DROP CONSTRAINT "UqProductosCategoriasRelacion";
ALTER TABLE "VariantesProducto"   DROP CONSTRAINT "UqVariantesProductoSKU";    -- era global entre tiendas
ALTER TABLE "Cupones"             DROP CONSTRAINT "UqCuponesTiendaCodigo";
ALTER TABLE "ItemsCarrito"        DROP CONSTRAINT "UqItemsCarritoVariante";

CREATE UNIQUE INDEX "UxPersonalTiendaUsuario"        ON "PersonalTienda"("TiendaId", "UsuarioId")      WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxCategoriasTiendaSlug"         ON "Categorias"("TiendaId", "IdentificadorUrl")   WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxProductosTiendaSlug"          ON "Productos"("TiendaId", "IdentificadorUrl")    WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxProductosCategoriasRelacion"  ON "ProductosCategorias"("ProductoId", "CategoriaId") WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxCuponesTiendaCodigo"          ON "Cupones"("TiendaId", UPPER("Codigo"))         WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxItemsCarritoVariante"         ON "ItemsCarrito"("CarritoId", "VarianteId")      WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxDireccionPrincipalUsuario"    ON "DireccionesUsuario"("UsuarioId")              WHERE "EsPrincipal" = TRUE AND "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxImagenPrincipalProducto"      ON "ImagenesProducto"("ProductoId")               WHERE "EsPrincipal" = TRUE AND "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxCarritoActivoUsuario"         ON "Carritos"("TiendaId", "UsuarioId")            WHERE "Estado" = 'ACTIVO' AND "UsuarioId" IS NOT NULL AND "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxOrdenesClaveIdempotencia"     ON "Ordenes"("TiendaId", "ClaveIdempotencia")     WHERE "ClaveIdempotencia" IS NOT NULL;
CREATE UNIQUE INDEX "UxOrdenesTokenAcceso"           ON "Ordenes"("TokenAcceso")                       WHERE "TokenAcceso" IS NOT NULL;
CREATE UNIQUE INDEX "UxPagosPasarelaTransaccion"     ON "Pagos"("PasarelaPago", "TransaccionId")       WHERE "TransaccionId" IS NOT NULL;
CREATE UNIQUE INDEX "UxEnviosOrden"                  ON "Envios"("OrdenId")                            WHERE "Eliminado" = FALSE;
DROP INDEX IF EXISTS "IdxEnviosOrden";  -- cubierto por UxEnviosOrden

ALTER TABLE "PlanesSuscripcion" ADD CONSTRAINT "UqPlanesSuscripcionCodigo" UNIQUE ("Codigo");
CREATE UNIQUE INDEX "UxSuscripcionVigenteTienda"     ON "SuscripcionesTienda"("TiendaId")              WHERE "Estado" <> 'CANCELADA' AND "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxCobroAprobadoPeriodo"         ON "CobrosSuscripcion"("SuscripcionId", "InicioPeriodo") WHERE "Estado" = 'APROBADO';
CREATE UNIQUE INDEX "UxCobrosPasarelaTransaccion"    ON "CobrosSuscripcion"("PasarelaPago", "TransaccionId") WHERE "TransaccionId" IS NOT NULL;

-- =================================================================================
-- SECCIÓN 4: CHECKS
-- =================================================================================

ALTER TABLE "PlanesSuscripcion"
    ADD CONSTRAINT "ChkPlanesPrecios"   CHECK ("PrecioMensual" >= 0 AND "PrecioAnual" >= 0),
    ADD CONSTRAINT "ChkPlanesLimites"   CHECK (("LimiteProductos" IS NULL OR "LimiteProductos" > 0) AND "LimitePersonal" >= 0),
    ADD CONSTRAINT "ChkPlanesComision"  CHECK ("PorcentajeComision" >= 0 AND "PorcentajeComision" <= 0.10),
    ADD CONSTRAINT "ChkPlanesDiasPrueba" CHECK ("DiasPrueba" >= 0);

ALTER TABLE "SuscripcionesTienda"
    ADD CONSTRAINT "ChkSuscripcionEstado" CHECK ("Estado" IN ('PRUEBA','ACTIVA','VENCIDA','CANCELADA','SUSPENDIDA')),
    ADD CONSTRAINT "ChkSuscripcionCiclo"  CHECK ("CicloFacturacion" IN ('MENSUAL','ANUAL')),
    ADD CONSTRAINT "ChkSuscripcionPeriodo" CHECK ("FinPeriodoActual" > "InicioPeriodoActual"),
    ADD CONSTRAINT "ChkSuscripcionPrecio" CHECK ("PrecioPactado" >= 0);

ALTER TABLE "CobrosSuscripcion"
    ADD CONSTRAINT "ChkCobrosEstado" CHECK ("Estado" IN ('PENDIENTE','APROBADO','RECHAZADO')),
    ADD CONSTRAINT "ChkCobrosMonto"  CHECK ("Monto" >= 0),
    ADD CONSTRAINT "ChkCobrosPeriodo" CHECK ("FinPeriodo" > "InicioPeriodo");

ALTER TABLE "TarifasEnvio"
    ADD CONSTRAINT "ChkTarifasPrecio" CHECK ("Precio" >= 0 AND ("MontoMinimoGratis" IS NULL OR "MontoMinimoGratis" >= 0));

ALTER TABLE "MovimientosInventario"
    ADD CONSTRAINT "ChkMovimientosTipo" CHECK ("Tipo" IN ('INICIAL','AJUSTE','RESERVA','LIBERACION','VENTA','REPOSICION')),
    ADD CONSTRAINT "ChkMovimientosReservado" CHECK ("ReservadoResultante" >= 0);

ALTER TABLE "Tiendas"
    ADD CONSTRAINT "ChkTiendasTasaImpuesto" CHECK ("TasaImpuesto" >= 0 AND "TasaImpuesto" <= 0.50),
    ADD CONSTRAINT "ChkTiendasSiguienteNumero" CHECK ("SiguienteNumeroOrden" > 0);

ALTER TABLE "Cupones"
    ADD CONSTRAINT "ChkCuponesTipo"  CHECK ("TipoDescuento" IN ('PORCENTAJE','MONTO_FIJO','ENVIO_GRATIS')),
    ADD CONSTRAINT "ChkCuponesValor" CHECK (
        ("TipoDescuento" = 'PORCENTAJE'   AND "ValorDescuento" > 0 AND "ValorDescuento" <= 100) OR
        ("TipoDescuento" = 'MONTO_FIJO'   AND "ValorDescuento" > 0) OR
        ("TipoDescuento" = 'ENVIO_GRATIS' AND "ValorDescuento" = 0)),
    ADD CONSTRAINT "ChkCuponesUsos"   CHECK ("CantidadUsosActual" >= 0 AND ("LimiteUsosTotal" IS NULL OR ("LimiteUsosTotal" > 0 AND "CantidadUsosActual" <= "LimiteUsosTotal"))),
    ADD CONSTRAINT "ChkCuponesFechas" CHECK ("FechaFin" IS NULL OR "FechaFin" > "FechaInicio");

ALTER TABLE "Carritos"
    ADD CONSTRAINT "ChkCarritosEstado" CHECK ("Estado" IN ('ACTIVO','CONVERTIDO'));

ALTER TABLE "ItemsCarrito"
    DROP CONSTRAINT "ChkItemsCarritoCantidad",
    ADD CONSTRAINT "ChkItemsCarritoCantidad" CHECK ("Cantidad" BETWEEN 1 AND 99);

ALTER TABLE "Ordenes"
    ADD CONSTRAINT "ChkOrdenesEstado"            CHECK ("Estado" IN ('ABIERTA','CERRADA','CANCELADA')),
    ADD CONSTRAINT "ChkOrdenesEstadoPago"        CHECK ("EstadoPago" IN ('PENDIENTE','PAGADO','PARCIALMENTE_REEMBOLSADO','REEMBOLSADO','ANULADO')),
    ADD CONSTRAINT "ChkOrdenesEstadoPreparacion" CHECK ("EstadoPreparacion" IN ('NO_PREPARADO','PREPARADO','ENTREGADO')),
    ADD CONSTRAINT "ChkOrdenesMontos"            CHECK ("DescuentoTotal" >= 0 AND "DescuentoTotal" <= "Subtotal" AND "CostoEnvio" >= 0 AND "ImpuestosTotal" >= 0 AND "ComisionPlataforma" >= 0),
    ADD CONSTRAINT "ChkOrdenesFormulaTotal"      CHECK ("Total" = "Subtotal" - "DescuentoTotal" + "CostoEnvio"
                                                        + CASE WHEN "PreciosIncluyenImpuesto" THEN 0 ELSE "ImpuestosTotal" END);

ALTER TABLE "ItemsOrden"
    ADD CONSTRAINT "ChkItemsOrdenTotalLinea" CHECK ("TotalLinea" = "PrecioUnitario" * "Cantidad");

ALTER TABLE "Pagos"
    ADD CONSTRAINT "ChkPagosTipo"   CHECK ("Tipo" IN ('CARGO','REEMBOLSO')),
    ADD CONSTRAINT "ChkPagosEstado" CHECK ("Estado" IN ('PENDIENTE','APROBADO','RECHAZADO')),
    ADD CONSTRAINT "ChkPagosMonto"  CHECK ("Monto" > 0),
    ADD CONSTRAINT "ChkPagosOrigenReembolso" CHECK (("Tipo" = 'REEMBOLSO') = ("PagoOrigenId" IS NOT NULL));

ALTER TABLE "Envios"
    ADD CONSTRAINT "ChkEnviosEstado" CHECK ("Estado" IN ('CREADO','EN_TRANSITO','ENTREGADO','DEVUELTO'));

-- =================================================================================
-- SECCIÓN 5: LLAVES FORÁNEAS
-- =================================================================================

ALTER TABLE "SuscripcionesTienda"
    ADD CONSTRAINT "FkSuscripcionesTiendaTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE RESTRICT,
    ADD CONSTRAINT "FkSuscripcionesTiendaPlan"   FOREIGN KEY ("PlanId")   REFERENCES "PlanesSuscripcion"("Id") ON DELETE RESTRICT;

ALTER TABLE "CobrosSuscripcion"
    ADD CONSTRAINT "FkCobrosSuscripcionSuscripcion" FOREIGN KEY ("SuscripcionId") REFERENCES "SuscripcionesTienda"("Id") ON DELETE RESTRICT,
    ADD CONSTRAINT "FkCobrosSuscripcionPlan"        FOREIGN KEY ("PlanId")        REFERENCES "PlanesSuscripcion"("Id") ON DELETE RESTRICT;

ALTER TABLE "TarifasEnvio"
    ADD CONSTRAINT "FkTarifasEnvioTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE CASCADE;

ALTER TABLE "MovimientosInventario"
    ADD CONSTRAINT "FkMovimientosInventarioTienda"   FOREIGN KEY ("TiendaId")   REFERENCES "Tiendas"("Id") ON DELETE RESTRICT,
    ADD CONSTRAINT "FkMovimientosInventarioVariante" FOREIGN KEY ("VarianteId") REFERENCES "VariantesProducto"("Id") ON DELETE RESTRICT,
    ADD CONSTRAINT "FkMovimientosInventarioOrden"    FOREIGN KEY ("OrdenId")    REFERENCES "Ordenes"("Id") ON DELETE RESTRICT;

ALTER TABLE "RegistroAuditoria"
    ADD CONSTRAINT "FkRegistroAuditoriaTienda"  FOREIGN KEY ("TiendaId")  REFERENCES "Tiendas"("Id")  ON DELETE SET NULL,
    ADD CONSTRAINT "FkRegistroAuditoriaUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE SET NULL;

ALTER TABLE "Carritos"
    ADD CONSTRAINT "FkCarritosOrden" FOREIGN KEY ("OrdenId") REFERENCES "Ordenes"("Id") ON DELETE SET NULL;

ALTER TABLE "Pagos"
    ADD CONSTRAINT "FkPagosPagoOrigen" FOREIGN KEY ("PagoOrigenId") REFERENCES "Pagos"("Id") ON DELETE RESTRICT;

-- =================================================================================
-- SECCIÓN 6: ÍNDICES DE RENDIMIENTO
-- =================================================================================

CREATE INDEX "IdxPersonalTiendaUsuario"      ON "PersonalTienda"("UsuarioId")                     WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxTiendasPropietario"         ON "Tiendas"("PropietarioId")                        WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxOrdenesUsuario"             ON "Ordenes"("UsuarioId", "FechaCreacion" DESC)      WHERE "Eliminado" = FALSE AND "UsuarioId" IS NOT NULL;
CREATE INDEX "IdxOrdenesTiendaFecha"         ON "Ordenes"("TiendaId", "FechaCreacion" DESC)       WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxOrdenesPendientesExpiracion" ON "Ordenes"("FechaExpiracionPago")                 WHERE "EstadoPago" = 'PENDIENTE' AND "Estado" = 'ABIERTA';
CREATE INDEX "IdxSuscripcionesRenovacion"    ON "SuscripcionesTienda"("Estado", "FinPeriodoActual") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxCobrosSuscripcion"          ON "CobrosSuscripcion"("SuscripcionId", "FechaCreacion" DESC);
CREATE INDEX "IdxTarifasEnvioTienda"         ON "TarifasEnvio"("TiendaId", "Orden")               WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxMovimientosVariante"        ON "MovimientosInventario"("VarianteId", "FechaCreacion" DESC);
CREATE INDEX "IdxAuditoriaTiendaFecha"       ON "RegistroAuditoria"("TiendaId", "FechaCreacion" DESC);
CREATE INDEX "IdxAuditoriaEntidad"           ON "RegistroAuditoria"("Entidad", "EntidadId");
CREATE INDEX "IdxVariantesSku"               ON "VariantesProducto"("SKU")                        WHERE "Eliminado" = FALSE;

-- =================================================================================
-- SECCIÓN 7: DATOS SEMILLA
-- =================================================================================

INSERT INTO "Roles" ("Nombre", "Codigo", "Descripcion") VALUES
    ('Propietario',        'PROPIETARIO',     'Dueño de la tienda. Control total, incluida la suscripción'),
    ('Administrador',      'ADMINISTRADOR',   'Opera toda la tienda salvo suscripción y propiedad'),
    ('Gestor de catálogo', 'GESTOR_CATALOGO', 'Productos, variantes, imágenes, categorías e inventario'),
    ('Gestor de pedidos',  'GESTOR_PEDIDOS',  'Órdenes: preparar, enviar y registrar pagos manuales')
ON CONFLICT ("Codigo") DO NOTHING;

-- D-005 (el comprador no es un rol de tienda): los roles 'CLIENTE' y 'ADMIN_TIENDA' NO se retiran aquí,
-- porque el registro actual los busca con "Eliminado" = FALSE y, si no los encuentra, intenta
-- insertarlos de nuevo (choca con UqRolesCodigo). Se retiran en V3, junto con la reescritura de usuario (T-003).

INSERT INTO "PlanesSuscripcion"
    ("Codigo", "Nombre", "Descripcion", "PrecioMensual", "PrecioAnual", "Moneda", "LimiteProductos", "LimitePersonal", "PorcentajeComision", "DiasPrueba", "Orden")
VALUES
    ('BASICO',   'Básico',   'Para empezar a vender',               29.00,  290.00, 'USD', 100,  2,  0.0200, 14, 1),
    ('ESTANDAR', 'Estándar', 'Para negocios en crecimiento',        79.00,  790.00, 'USD', 1000, 5,  0.0100, 14, 2),
    ('AVANZADO', 'Avanzado', 'Para marcas con alto volumen',       299.00, 2990.00, 'USD', NULL, 15, 0.0050, 14, 3)
ON CONFLICT ("Codigo") DO NOTHING;

-- =================================================================================
-- SECCIÓN 8: DATOS EXISTENTES (tiendas creadas antes de V002)
-- =================================================================================

-- Propietario como personal
INSERT INTO "PersonalTienda" ("TiendaId", "UsuarioId", "RolId")
SELECT t."Id", t."PropietarioId", r."Id"
FROM "Tiendas" t
JOIN "Roles" r ON r."Codigo" = 'PROPIETARIO'
WHERE t."Eliminado" = FALSE
  AND NOT EXISTS (SELECT 1 FROM "PersonalTienda" p
                  WHERE p."TiendaId" = t."Id" AND p."UsuarioId" = t."PropietarioId" AND p."Eliminado" = FALSE);

-- Balance
INSERT INTO "BalancesTienda" ("TiendaId")
SELECT t."Id" FROM "Tiendas" t
WHERE NOT EXISTS (SELECT 1 FROM "BalancesTienda" b WHERE b."TiendaId" = t."Id");

-- Suscripción de prueba al plan BÁSICO desde hoy
INSERT INTO "SuscripcionesTienda"
    ("TiendaId", "PlanId", "Estado", "CicloFacturacion", "PrecioPactado", "Moneda",
     "FechaFinPrueba", "InicioPeriodoActual", "FinPeriodoActual")
SELECT t."Id", p."Id", 'PRUEBA', 'MENSUAL', p."PrecioMensual", p."Moneda",
       CURRENT_TIMESTAMP + (p."DiasPrueba" || ' days')::INTERVAL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP + (p."DiasPrueba" || ' days')::INTERVAL
FROM "Tiendas" t
JOIN "PlanesSuscripcion" p ON p."Codigo" = 'BASICO'
WHERE t."Eliminado" = FALSE
  AND NOT EXISTS (SELECT 1 FROM "SuscripcionesTienda" s
                  WHERE s."TiendaId" = t."Id" AND s."Estado" <> 'CANCELADA' AND s."Eliminado" = FALSE);


-- Para promover un administrador de plataforma (ejecutar a mano, con el correo real):
-- UPDATE "Usuarios" SET "EsAdministradorPlataforma" = TRUE WHERE "CorreoElectronico" = '<correo>';
