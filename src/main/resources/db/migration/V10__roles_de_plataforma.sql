-- ==============================================================================
-- V10: roles de plataforma y administradores con permisos (R-001, B9 etapa 1)
--   * "RolesPlataforma" + "RolesPlataformaPermisos": el catálogo de permisos vive en código (PermisoPlataforma);
--     la base solo dice qué permisos tiene cada rol.
--   * "Usuarios"."RolPlataformaId": un administrador de plataforma tiene un rol. La bandera EsAdministradorPlataforma
--     se mantiene como superadministrador (todos los permisos, no se edita por la API).
--   * Tres roles de sistema, no editables.
-- ==============================================================================

CREATE TABLE "RolesPlataforma" (
    "Id"              UUID DEFAULT gen_random_uuid() NOT NULL,
    "Codigo"          VARCHAR(40)  NOT NULL,
    "Nombre"          VARCHAR(100) NOT NULL,
    "Descripcion"     VARCHAR(255),
    "EsSistema"       BOOLEAN DEFAULT FALSE NOT NULL,
    "FechaCreacion"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "UsuarioCreacion" UUID,
    "FechaEdicion"    TIMESTAMP WITH TIME ZONE,
    "UsuarioEdicion"  UUID,
    "Eliminado"       BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT "PkRolesPlataforma" PRIMARY KEY ("Id")
);
CREATE UNIQUE INDEX "UxRolesPlataformaCodigo" ON "RolesPlataforma"(UPPER("Codigo")) WHERE "Eliminado" = FALSE;

CREATE TABLE "RolesPlataformaPermisos" (
    "RolId"   UUID NOT NULL,
    "Permiso" VARCHAR(60) NOT NULL,
    CONSTRAINT "PkRolesPlataformaPermisos" PRIMARY KEY ("RolId", "Permiso"),
    CONSTRAINT "FkRolesPlataformaPermisosRol" FOREIGN KEY ("RolId") REFERENCES "RolesPlataforma"("Id") ON DELETE CASCADE
);

ALTER TABLE "Usuarios" ADD COLUMN "RolPlataformaId" UUID;
ALTER TABLE "Usuarios"
    ADD CONSTRAINT "FkUsuariosRolPlataforma" FOREIGN KEY ("RolPlataformaId") REFERENCES "RolesPlataforma"("Id") ON DELETE RESTRICT;
CREATE INDEX "IdxUsuariosRolPlataforma" ON "Usuarios"("RolPlataformaId") WHERE "RolPlataformaId" IS NOT NULL;

INSERT INTO "RolesPlataforma" ("Codigo", "Nombre", "Descripcion", "EsSistema") VALUES
    ('ADMIN_PLATAFORMA', 'Administrador de plataforma', 'Todos los permisos, incluida la gestión de administradores', TRUE),
    ('SOPORTE', 'Soporte', 'Consulta tiendas, usuarios, pedidos y auditoría', TRUE),
    ('FINANZAS', 'Finanzas', 'Planes, pedidos, repartos y comisiones', TRUE);

INSERT INTO "RolesPlataformaPermisos" ("RolId", "Permiso")
SELECT r."Id", p.permiso
FROM "RolesPlataforma" r
JOIN (VALUES
    ('ADMIN_PLATAFORMA', 'PLATAFORMA_TIENDAS'), ('ADMIN_PLATAFORMA', 'PLATAFORMA_SUSPENDER_TIENDAS'),
    ('ADMIN_PLATAFORMA', 'PLATAFORMA_USUARIOS'), ('ADMIN_PLATAFORMA', 'PLATAFORMA_PLANES'),
    ('ADMIN_PLATAFORMA', 'PLATAFORMA_PEDIDOS'), ('ADMIN_PLATAFORMA', 'PLATAFORMA_AUDITORIA'),
    ('ADMIN_PLATAFORMA', 'PLATAFORMA_SEGURIDAD'), ('ADMIN_PLATAFORMA', 'PLATAFORMA_ADMINISTRADORES'),
    ('SOPORTE', 'PLATAFORMA_TIENDAS'), ('SOPORTE', 'PLATAFORMA_USUARIOS'),
    ('SOPORTE', 'PLATAFORMA_PEDIDOS'), ('SOPORTE', 'PLATAFORMA_AUDITORIA'),
    ('FINANZAS', 'PLATAFORMA_TIENDAS'), ('FINANZAS', 'PLATAFORMA_PLANES'), ('FINANZAS', 'PLATAFORMA_PEDIDOS')
) AS p(codigo, permiso) ON p.codigo = r."Codigo";
