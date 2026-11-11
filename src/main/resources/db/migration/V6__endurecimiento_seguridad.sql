-- ==============================================================================
-- V6: endurecimiento de seguridad (A-007)
--   * Revocación de sesiones: los JWT emitidos antes de "SesionesValidasDesde" dejan de valer.
--   * Bloqueo temporal de la cuenta por intentos fallidos de login.
--   * Segundo factor por correo, opcional por usuario.
--   * Nuevos tipos de código de un solo uso.
-- ==============================================================================

ALTER TABLE "Usuarios"
    ADD COLUMN "SesionesValidasDesde"  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN "IntentosLoginFallidos" INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN "BloqueadoHasta"        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN "DosPasosActivo"        BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE "Usuarios"
    ADD CONSTRAINT "ChkUsuariosIntentosLogin" CHECK ("IntentosLoginFallidos" >= 0);

ALTER TABLE "TokensSeguridad"
    DROP CONSTRAINT "ChkTokensSeguridadTipo";

ALTER TABLE "TokensSeguridad"
    ADD CONSTRAINT "ChkTokensSeguridadTipo"
        CHECK ("Tipo" IN ('ACTIVACION_CUENTA', 'RECUPERACION_CLAVE', 'DESBLOQUEO_CUENTA', 'SEGUNDO_FACTOR'));
