-- =================================================================================
-- MIGRACIÓN V5: RUTAS DE REPARTO, SEGUIMIENTO DEL ENVÍO Y PUNTO DE ENTREGA (A-004)
-- Flyway la ejecuta en una transacción (sin BEGIN/COMMIT).
-- =================================================================================

-- 1. Punto de entrega opcional en la libreta de direcciones del comprador (ENV-007 lo exige solo al comprar)
ALTER TABLE "DireccionesUsuario"
    ADD COLUMN "Latitud"  DECIMAL(9, 6),
    ADD COLUMN "Longitud" DECIMAL(9, 6),
    ADD CONSTRAINT "ChkDireccionesUsuarioLatitud"  CHECK ("Latitud"  IS NULL OR ("Latitud"  BETWEEN -90  AND 90)),
    ADD CONSTRAINT "ChkDireccionesUsuarioLongitud" CHECK ("Longitud" IS NULL OR ("Longitud" BETWEEN -180 AND 180));

-- 2. Historial de eventos del envío (seguimiento). Solo se añaden filas.
CREATE TABLE "EventosEnvio" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "EnvioId" UUID NOT NULL,
    "Estado" VARCHAR(50) NOT NULL,
    "Nota" VARCHAR(500),
    "FotoUrl" TEXT,
    "FechaEvento" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkEventosEnvio" PRIMARY KEY ("Id"),
    CONSTRAINT "FkEventosEnvioEnvio" FOREIGN KEY ("EnvioId") REFERENCES "Envios"("Id") ON DELETE RESTRICT,
    CONSTRAINT "ChkEventosEnvioEstado" CHECK ("Estado" IN ('CREADO', 'EN_TRANSITO', 'ENTREGADO', 'NO_ENTREGADO', 'DEVUELTO')),
    CONSTRAINT "ChkEventosEnvioNota" CHECK ("Estado" <> 'NO_ENTREGADO' OR ("Nota" IS NOT NULL AND length(btrim("Nota")) > 0))
);

CREATE INDEX "IdxEventosEnvioEnvio" ON "EventosEnvio" ("EnvioId", "FechaEvento" DESC) WHERE "Eliminado" = FALSE;

-- 3. Hojas de ruta de reparto y sus paradas (RUT-001 a RUT-005)
CREATE TABLE "Rutas" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "TiendaId" UUID NOT NULL,
    "Codigo" VARCHAR(20) NOT NULL,
    "Fecha" DATE NOT NULL,
    "Chofer" VARCHAR(150) NOT NULL,
    "Placa" VARCHAR(20) NOT NULL,
    "Estado" VARCHAR(20) DEFAULT 'BORRADOR' NOT NULL,
    "Nota" VARCHAR(500),
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkRutas" PRIMARY KEY ("Id"),
    CONSTRAINT "FkRutasTienda" FOREIGN KEY ("TiendaId") REFERENCES "Tiendas"("Id") ON DELETE RESTRICT,
    CONSTRAINT "ChkRutasEstado" CHECK ("Estado" IN ('BORRADOR', 'EN_CURSO', 'FINALIZADA'))
);

-- El código no se reutiliza ni siquiera si la ruta se elimina
CREATE UNIQUE INDEX "UxRutasTiendaCodigo" ON "Rutas" ("TiendaId", "Codigo");
CREATE INDEX "IdxRutasTiendaFecha" ON "Rutas" ("TiendaId", "Fecha" DESC) WHERE "Eliminado" = FALSE;

CREATE TABLE "ParadasRuta" (
    "Id" UUID DEFAULT gen_random_uuid(),
    "RutaId" UUID NOT NULL,
    "EnvioId" UUID NOT NULL,
    "Posicion" INTEGER NOT NULL,
    "FechaCreacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion" TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion" UUID,
    "Eliminado" BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkParadasRuta" PRIMARY KEY ("Id"),
    CONSTRAINT "FkParadasRutaRuta" FOREIGN KEY ("RutaId") REFERENCES "Rutas"("Id") ON DELETE RESTRICT,
    CONSTRAINT "FkParadasRutaEnvio" FOREIGN KEY ("EnvioId") REFERENCES "Envios"("Id") ON DELETE RESTRICT,
    CONSTRAINT "ChkParadasRutaPosicion" CHECK ("Posicion" > 0)
);

CREATE UNIQUE INDEX "UxParadasRutaPosicion" ON "ParadasRuta" ("RutaId", "Posicion") WHERE "Eliminado" = FALSE;
CREATE UNIQUE INDEX "UxParadasRutaEnvio" ON "ParadasRuta" ("RutaId", "EnvioId") WHERE "Eliminado" = FALSE;
CREATE INDEX "IdxParadasRutaEnvio" ON "ParadasRuta" ("EnvioId") WHERE "Eliminado" = FALSE;
