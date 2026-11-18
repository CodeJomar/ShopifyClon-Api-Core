-- ==============================================================================
-- V11: reseñas y calificaciones de productos (R-011)
--   Una reseña por persona y producto (no por variante): calificación de 1 a 5 y comentario opcional.
--   Solo reseña quien tiene una orden pagada con ese producto y no es miembro de la tienda (lo valida la API).
--   Borrar es lógico ("Eliminado"): la persona puede volver a reseñar. "Oculta" es la moderación de plataforma.
-- ==============================================================================

CREATE TABLE "ResenasProducto" (
    "Id"               UUID DEFAULT gen_random_uuid() NOT NULL,
    "ProductoId"       UUID NOT NULL,
    "TiendaId"         UUID NOT NULL,
    "UsuarioId"        UUID NOT NULL,
    "Calificacion"     INTEGER NOT NULL,
    "Comentario"       VARCHAR(1000),
    "Oculta"           BOOLEAN DEFAULT FALSE NOT NULL,
    "MotivoOcultacion" VARCHAR(255),
    "FechaCreacion"    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion"  UUID,
    "FechaEdicion"     TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion"   UUID,
    "Eliminado"        BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkResenasProducto" PRIMARY KEY ("Id"),
    CONSTRAINT "FkResenasProductoProducto" FOREIGN KEY ("ProductoId") REFERENCES "Productos"("Id") ON DELETE RESTRICT,
    CONSTRAINT "FkResenasProductoTienda"   FOREIGN KEY ("TiendaId")   REFERENCES "Tiendas"("Id")   ON DELETE RESTRICT,
    CONSTRAINT "FkResenasProductoUsuario"  FOREIGN KEY ("UsuarioId")  REFERENCES "Usuarios"("Id")  ON DELETE CASCADE,
    CONSTRAINT "ChkResenasProductoCalificacion" CHECK ("Calificacion" BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX "UxResenasProductoUsuario" ON "ResenasProducto"("ProductoId", "UsuarioId") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxResenasProductoVisibles" ON "ResenasProducto"("ProductoId", "FechaCreacion" DESC)
    WHERE "Eliminado" = FALSE AND "Oculta" = FALSE;
CREATE INDEX "IdxResenasProductoUsuario" ON "ResenasProducto"("UsuarioId") WHERE "Eliminado" = FALSE;
