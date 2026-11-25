#!/bin/sh
# Entrada de la imagen. Arranca como root SOLO para dejar escribible el directorio de respaldos (un volumen montado en
# Railway o con Docker pertenece a root) y enseguida baja a "appuser": la API nunca corre con privilegios.
set -e

DIR="${BACKUP_DIRECTORIO:-/app/respaldos}"

if [ "$(id -u)" = "0" ]; then
  mkdir -p "$DIR" 2>/dev/null || true
  chown -R appuser:appgroup "$DIR" 2>/dev/null || echo "Aviso: no se pudieron ajustar los permisos de $DIR (los respaldos podrian fallar)"
  cd /app
  exec su-exec appuser:appgroup java ${JAVA_OPTS} -jar app.jar
fi

cd /app
exec java ${JAVA_OPTS} -jar app.jar
