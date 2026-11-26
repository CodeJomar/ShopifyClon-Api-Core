-- ==============================================================================
-- V14: apariencia (personalización) de la vitrina de cada tienda (R-031)
--   * Cinco opciones fijas con lista blanca en la base (CHECK) y en la API. Las tiendas existentes arrancan con los
--     valores por defecto. El logo ya existe en "Tiendas"."UrlLogo".
-- ==============================================================================

ALTER TABLE "Tiendas" ADD COLUMN "Paleta"      VARCHAR(20) DEFAULT 'OTEMU'      NOT NULL;
ALTER TABLE "Tiendas" ADD COLUMN "Tipografia"  VARCHAR(20) DEFAULT 'MODERNA'    NOT NULL;
ALTER TABLE "Tiendas" ADD COLUMN "TamanoLetra" VARCHAR(20) DEFAULT 'NORMAL'     NOT NULL;
ALTER TABLE "Tiendas" ADD COLUMN "Diseno"      VARCHAR(20) DEFAULT 'CUADRICULA' NOT NULL;
ALTER TABLE "Tiendas" ADD COLUMN "Modo"        VARCHAR(20) DEFAULT 'OSCURO'     NOT NULL;

ALTER TABLE "Tiendas" ADD CONSTRAINT "CkTiendasPaleta"
    CHECK ("Paleta" IN ('OTEMU', 'OCEANO', 'ATARDECER', 'BOSQUE', 'GRAFITO', 'ROSA'));
ALTER TABLE "Tiendas" ADD CONSTRAINT "CkTiendasTipografia"
    CHECK ("Tipografia" IN ('MODERNA', 'CLASICA', 'REDONDEADA', 'COMPACTA'));
ALTER TABLE "Tiendas" ADD CONSTRAINT "CkTiendasTamanoLetra"
    CHECK ("TamanoLetra" IN ('NORMAL', 'GRANDE', 'MUY_GRANDE'));
ALTER TABLE "Tiendas" ADD CONSTRAINT "CkTiendasDiseno"
    CHECK ("Diseno" IN ('CUADRICULA', 'REVISTA', 'LISTA', 'PORTADA'));
ALTER TABLE "Tiendas" ADD CONSTRAINT "CkTiendasModo"
    CHECK ("Modo" IN ('OSCURO', 'CLARO', 'ALTO_CONTRASTE'));
