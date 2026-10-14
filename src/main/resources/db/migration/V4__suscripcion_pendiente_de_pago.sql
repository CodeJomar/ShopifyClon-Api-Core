-- =================================================================================
-- MIGRACIÓN V4: SUSCRIPCIÓN PENDIENTE DE PAGO (T-005, SUS-016)
-- Una tienda cuyo propietario ya usó su prueba nace en estado VENCIDA con período vacío
-- (inicio = fin = momento del alta). El CHECK de V2 exigía fin > inicio.
-- =================================================================================

ALTER TABLE "SuscripcionesTienda"
    DROP CONSTRAINT "ChkSuscripcionPeriodo",
    ADD CONSTRAINT "ChkSuscripcionPeriodo" CHECK ("FinPeriodoActual" >= "InicioPeriodoActual");
