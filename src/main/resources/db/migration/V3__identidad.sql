-- =================================================================================
-- MIGRACIÓN V3: IDENTIDAD (T-003)
-- - Retira los roles 'CLIENTE' y 'ADMIN_TIENDA' (D-005) convirtiendo antes el personal que los usa.
-- - Tokens de seguridad hasheados con límite de intentos (USR-007, USR-012).
-- - Correo único sin distinguir mayúsculas (USR-001).
-- Flyway la ejecuta en una transacción (sin BEGIN/COMMIT).
-- =================================================================================

-- 1. Personal: el propietario siempre figura como PROPIETARIO
UPDATE "PersonalTienda" p
SET "RolId" = (SELECT "Id" FROM "Roles" WHERE "Codigo" = 'PROPIETARIO'),
    "FechaEdicion" = CURRENT_TIMESTAMP
FROM "Tiendas" t
WHERE p."TiendaId" = t."Id"
  AND p."UsuarioId" = t."PropietarioId"
  AND p."Eliminado" = FALSE;

-- 2. Personal: ADMIN_TIENDA que no es propietario pasa a ADMINISTRADOR
UPDATE "PersonalTienda" p
SET "RolId" = (SELECT "Id" FROM "Roles" WHERE "Codigo" = 'ADMINISTRADOR'),
    "FechaEdicion" = CURRENT_TIMESTAMP
FROM "Roles" r
WHERE p."RolId" = r."Id"
  AND r."Codigo" = 'ADMIN_TIENDA'
  AND p."Eliminado" = FALSE;

-- 3. Personal: CLIENTE no es un rol de tienda
UPDATE "PersonalTienda" p
SET "Eliminado" = TRUE,
    "FechaEdicion" = CURRENT_TIMESTAMP
FROM "Roles" r
WHERE p."RolId" = r."Id"
  AND r."Codigo" = 'CLIENTE'
  AND p."Eliminado" = FALSE;

-- 4. Propietarios que todavía no tienen fila de personal
INSERT INTO "PersonalTienda" ("TiendaId", "UsuarioId", "RolId")
SELECT t."Id", t."PropietarioId", r."Id"
FROM "Tiendas" t
JOIN "Roles" r ON r."Codigo" = 'PROPIETARIO'
WHERE t."Eliminado" = FALSE
  AND NOT EXISTS (SELECT 1 FROM "PersonalTienda" p
                  WHERE p."TiendaId" = t."Id" AND p."UsuarioId" = t."PropietarioId" AND p."Eliminado" = FALSE);

-- 5. Retirar los roles viejos
UPDATE "Roles"
SET "Eliminado" = TRUE,
    "FechaEdicion" = CURRENT_TIMESTAMP
WHERE "Codigo" IN ('CLIENTE', 'ADMIN_TIENDA');

-- 6. Tokens de seguridad: intentos fallidos, tipos válidos, búsqueda por usuario
ALTER TABLE "TokensSeguridad"
    ADD COLUMN "IntentosFallidos" INTEGER DEFAULT 0 NOT NULL;

-- Los tokens previos se guardaron en texto plano: se invalidan todos los pendientes
UPDATE "TokensSeguridad"
SET "Usado" = TRUE,
    "FechaEdicion" = CURRENT_TIMESTAMP
WHERE "Usado" = FALSE;

ALTER TABLE "TokensSeguridad"
    ADD CONSTRAINT "ChkTokensSeguridadTipo"      CHECK ("Tipo" IN ('ACTIVACION_CUENTA', 'RECUPERACION_CLAVE')),
    ADD CONSTRAINT "ChkTokensSeguridadIntentos"  CHECK ("IntentosFallidos" >= 0);

DROP INDEX IF EXISTS "IdxTokensSeguridadValidacion";
CREATE INDEX "IdxTokensSeguridadUsuarioTipo"
    ON "TokensSeguridad" ("UsuarioId", "Tipo", "FechaCreacion" DESC)
    WHERE "Usado" = FALSE AND "Eliminado" = FALSE;

-- 7. Correo único sin distinguir mayúsculas
CREATE UNIQUE INDEX "UxUsuariosCorreoMinusculas" ON "Usuarios" (LOWER("CorreoElectronico"));
