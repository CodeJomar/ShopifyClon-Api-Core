-- ==============================================================================
-- V8: pedido padre (R-001, B5)
--   Una compra con productos de varias tiendas es un PEDIDO con una ORDEN hija por tienda. Cada orden hija es
--   exactamente la de siempre (líneas, totales, preparación, envío, balance). Todo dato histórico se rellena como
--   un pedido de una sola orden, reutilizando el id de la orden como id del pedido.
-- ==============================================================================

CREATE SEQUENCE "SecuenciaNumeroPedido" START WITH 1;

CREATE TABLE "Pedidos" (
    "Id"                  UUID DEFAULT gen_random_uuid() NOT NULL,
    "UsuarioId"           UUID,                                  -- opcional: compra como invitado
    "NumeroPedido"        VARCHAR(30)  NOT NULL,                 -- global, "P-000123"
    "CorreoCliente"       VARCHAR(255) NOT NULL,
    "NombreCliente"       VARCHAR(150) NOT NULL,
    "TelefonoCliente"     VARCHAR(30),
    "Moneda"              VARCHAR(3)   NOT NULL,
    "Subtotal"            DECIMAL(12, 2) NOT NULL,
    "DescuentoTotal"      DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    "CostoEnvio"          DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    "ImpuestosTotal"      DECIMAL(12, 2) DEFAULT 0.00 NOT NULL,
    "Total"               DECIMAL(12, 2) NOT NULL,
    "PasarelaPago"        VARCHAR(50),
    "ClaveIdempotencia"   UUID,
    "TokenAcceso"         VARCHAR(100) NOT NULL,
    "FechaExpiracionPago" TIMESTAMP WITH TIME ZONE,
    "FechaCreacion"       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion"     UUID,
    "FechaEdicion"        TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion"      UUID,
    "Eliminado"           BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkPedidos" PRIMARY KEY ("Id"),
    CONSTRAINT "FkPedidosUsuario" FOREIGN KEY ("UsuarioId") REFERENCES "Usuarios"("Id") ON DELETE SET NULL,
    CONSTRAINT "ChkPedidosMontos" CHECK ("Subtotal" >= 0 AND "DescuentoTotal" >= 0 AND "CostoEnvio" >= 0
                                         AND "ImpuestosTotal" >= 0 AND "Total" >= 0)
);

CREATE UNIQUE INDEX "UxPedidosNumero"             ON "Pedidos"("NumeroPedido");
CREATE UNIQUE INDEX "UxPedidosTokenAcceso"        ON "Pedidos"("TokenAcceso");
CREATE UNIQUE INDEX "UxPedidosClaveIdempotencia"  ON "Pedidos"("ClaveIdempotencia") WHERE "ClaveIdempotencia" IS NOT NULL;
CREATE INDEX "IdxPedidosUsuario" ON "Pedidos"("UsuarioId", "FechaCreacion" DESC) WHERE "Eliminado" = FALSE AND "UsuarioId" IS NOT NULL;

ALTER TABLE "Ordenes" ADD COLUMN "PedidoId" UUID;

-- Relleno: cada orden existente es un pedido de una hija (mismo id)
INSERT INTO "Pedidos" ("Id", "UsuarioId", "NumeroPedido", "CorreoCliente", "NombreCliente", "TelefonoCliente", "Moneda",
                       "Subtotal", "DescuentoTotal", "CostoEnvio", "ImpuestosTotal", "Total", "PasarelaPago",
                       "TokenAcceso", "FechaExpiracionPago", "FechaCreacion", "Eliminado")
SELECT o."Id", o."UsuarioId", 'P-' || lpad(nextval('"SecuenciaNumeroPedido"')::text, 6, '0'), o."CorreoCliente",
       o."NombreCliente", o."TelefonoCliente", t."Moneda", o."Subtotal", o."DescuentoTotal", o."CostoEnvio",
       o."ImpuestosTotal", o."Total", o."PasarelaPago",
       replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''),
       o."FechaExpiracionPago", o."FechaCreacion", o."Eliminado"
FROM (SELECT * FROM "Ordenes" ORDER BY "FechaCreacion", "Id") o
JOIN "Tiendas" t ON t."Id" = o."TiendaId";

UPDATE "Ordenes" SET "PedidoId" = "Id";

ALTER TABLE "Ordenes"
    ADD CONSTRAINT "FkOrdenesPedido" FOREIGN KEY ("PedidoId") REFERENCES "Pedidos"("Id") ON DELETE RESTRICT;
CREATE INDEX "IdxOrdenesPedido" ON "Ordenes"("PedidoId") WHERE "PedidoId" IS NOT NULL;
