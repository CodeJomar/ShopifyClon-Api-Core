-- ==============================================================================
-- V13: RUC de la tienda (R-030)
--   * "Ruc": 11 dígitos. Es obligatorio al crear una tienda (lo exige la API); la columna admite NULL porque las
--     tiendas anteriores a esta migración quedan sin RUC hasta que su propietario lo completa en la edición.
--   * Único POR PROPIETARIO: el mismo RUC puede estar en varias tiendas de la misma persona, pero no en tiendas de
--     dos propietarios distintos. Un índice único no puede expresarlo; lo garantiza una restricción de exclusión
--     (btree_gist) que rechaza dos tiendas no eliminadas con el mismo RUC y distinto propietario.
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE "Tiendas" ADD COLUMN "Ruc" VARCHAR(11);
ALTER TABLE "Tiendas" ADD CONSTRAINT "CkTiendasRuc" CHECK ("Ruc" IS NULL OR "Ruc" ~ '^[0-9]{11}$');
ALTER TABLE "Tiendas" ADD CONSTRAINT "ExTiendasRucPorPropietario"
    EXCLUDE USING gist ("Ruc" WITH =, "PropietarioId" WITH <>) WHERE ("Ruc" IS NOT NULL AND "Eliminado" = FALSE);
