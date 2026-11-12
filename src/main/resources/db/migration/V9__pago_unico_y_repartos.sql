-- ==============================================================================
-- V9: pago único de un pedido y libro de repartos (R-001, B6)
--   * Un cobro de tarjeta (un PaymentIntent) cubre todas las órdenes hijas del pedido: cada orden conserva su propia
--     fila en "Pagos" (por su monto), todas con la misma transacción. La unicidad pasa a incluir la orden.
--   * "RepartosPago": un asiento auditable por orden y movimiento (pedido, orden, tienda, bruto, comisión, neto).
--     Las tiendas se pagan después con el libro de balances (no hay Stripe Connect).
-- ==============================================================================

DROP INDEX "UxPagosPasarelaTransaccion";
CREATE UNIQUE INDEX "UxPagosPasarelaTransaccionOrden" ON "Pagos"("PasarelaPago", "TransaccionId", "OrdenId")
    WHERE "TransaccionId" IS NOT NULL;
CREATE INDEX "IdxPagosTransaccion" ON "Pagos"("PasarelaPago", "TransaccionId") WHERE "TransaccionId" IS NOT NULL;

CREATE TABLE "RepartosPago" (
    "Id"              UUID DEFAULT gen_random_uuid() NOT NULL,
    "PedidoId"        UUID,
    "OrdenId"         UUID NOT NULL,
    "TiendaId"        UUID NOT NULL,
    "Tipo"            VARCHAR(20) DEFAULT 'VENTA' NOT NULL,      -- 'VENTA' (semana 3: 'REEMBOLSO' con valores negativos)
    "Bruto"           DECIMAL(12, 2) NOT NULL,
    "Comision"        DECIMAL(12, 2) NOT NULL,
    "Neto"            DECIMAL(12, 2) NOT NULL,
    "Moneda"          VARCHAR(3) NOT NULL,
    "FechaCreacion"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion"    TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion"  UUID,
    "Eliminado"       BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkRepartosPago" PRIMARY KEY ("Id"),
    CONSTRAINT "FkRepartosPagoPedido" FOREIGN KEY ("PedidoId") REFERENCES "Pedidos"("Id") ON DELETE RESTRICT,
    CONSTRAINT "FkRepartosPagoOrden"  FOREIGN KEY ("OrdenId")  REFERENCES "Ordenes"("Id") ON DELETE RESTRICT,
    CONSTRAINT "FkRepartosPagoTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE RESTRICT,
    CONSTRAINT "ChkRepartosPagoTipo"  CHECK ("Tipo" IN ('VENTA', 'REEMBOLSO')),
    CONSTRAINT "ChkRepartosPagoNeto"  CHECK ("Neto" = "Bruto" - "Comision")
);

CREATE UNIQUE INDEX "UxRepartosPagoVentaOrden" ON "RepartosPago"("OrdenId") WHERE "Tipo" = 'VENTA';
CREATE INDEX "IdxRepartosPagoTienda" ON "RepartosPago"("TiendaId", "FechaCreacion" DESC);
CREATE INDEX "IdxRepartosPagoPedido" ON "RepartosPago"("PedidoId") WHERE "PedidoId" IS NOT NULL;
