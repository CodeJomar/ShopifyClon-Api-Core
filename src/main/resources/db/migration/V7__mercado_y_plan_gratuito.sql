-- ==============================================================================
-- V7: mercado público y plan gratuito (R-001, semana 1 del rediseño)
--   * La tienda gana descripción, logo y un interruptor para aparecer en el mercado.
--   * Plan GRATIS: 0 USD, sin prueba ni vencimiento (la suscripción nace ACTIVA).
--   * Índice para el listado de productos recientes del mercado.
-- Compatible con una base vacía: no depende de datos previos salvo el catálogo de planes de V2.
-- ==============================================================================

ALTER TABLE "Tiendas"
    ADD COLUMN "Descripcion"       VARCHAR(500),
    ADD COLUMN "UrlLogo"           VARCHAR(2000),
    ADD COLUMN "VisibleEnMercado"  BOOLEAN DEFAULT TRUE NOT NULL;

INSERT INTO "PlanesSuscripcion"
    ("Codigo", "Nombre", "Descripcion", "PrecioMensual", "PrecioAnual", "Moneda", "LimiteProductos", "LimitePersonal", "PorcentajeComision", "DiasPrueba", "Orden")
VALUES
    ('GRATIS', 'Gratuito', 'Para empezar sin costo: una tienda gratuita, hasta 20 productos', 0.00, 0.00, 'USD', 20, 0, 0.0500, 0, 0)
ON CONFLICT ("Codigo") DO NOTHING;

CREATE INDEX "IdxProductosMercadoRecientes"
    ON "Productos" ("FechaCreacion" DESC, "Id")
    WHERE "EstaPublicado" = TRUE AND "Eliminado" = FALSE;
