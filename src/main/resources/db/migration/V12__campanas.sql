-- ==============================================================================
-- V12: campaña comercial vigente (R-028)
--   * Una sola fila (Id = 1): "Codigo" es la campaña activa o NULL si no hay ninguna.
--   * Las campañas y sus tramos de descuento son constantes del código (Campana, TramosCampana); la base solo
--     recuerda cuál está vigente y quién la cambió por última vez.
-- ==============================================================================

CREATE TABLE "CampanaActiva" (
    "Id"             SMALLINT DEFAULT 1 NOT NULL,
    "Codigo"         VARCHAR(30),
    "FechaEdicion"   TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    CONSTRAINT "PkCampanaActiva" PRIMARY KEY ("Id"),
    CONSTRAINT "CkCampanaActivaUnica" CHECK ("Id" = 1)
);
INSERT INTO "CampanaActiva" ("Id") VALUES (1);
