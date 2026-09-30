# ==============================================================================
# Dockerfile — ShopifyClon API Core
# Multi-stage build para Spring Boot 3.3.4 + Java 17
# ==============================================================================
# Imagen resultante: ~280 MB (JRE 17 Alpine + fat JAR)
#
# Build:  docker build -t shopifyclon-api:local .
# Run:    docker run --rm --env-file .env -p 8080:8080 shopifyclon-api:local
#
# Decisiones documentadas:
#   - NO se usa Layered JAR: el proyecto tiene pocas dependencias estables y la
#     complejidad adicional no justifica el beneficio marginal de cache.
#     Si las dependencias crecen significativamente, reconsiderar.
#   - Tests se omiten en el build de imagen (-DskipTests) porque deben ejecutarse
#     previamente en el pipeline CI/CD. Si se desea ejecutarlos dentro del build,
#     remover el flag.
#   - Healthcheck NO se incluye en el Dockerfile porque no existe Spring Boot
#     Actuator en el proyecto. El healthcheck debe configurarse externamente
#     (Kubernetes liveness/readiness, ECS health check, etc.) apuntando a algún
#     endpoint existente como GET /api/v1/swagger-ui/index.html o un futuro
#     /actuator/health.
#   - No se hardcodea SPRING_PROFILES_ACTIVE. La misma imagen sirve para
#     DEV, QA y PROD cambiando solo variables de entorno externas.
# ==============================================================================

# ------------------------------------------------------------------------------
# STAGE 1: BUILD — Compilación con Maven Wrapper + JDK 17
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

# 1. Copiar solo archivos de Maven para resolver dependencias (cache optimizado)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Dar permisos de ejecución al wrapper (puede venir sin +x desde Windows)
RUN chmod +x mvnw

# 2. Descargar dependencias offline (capa cacheada si pom.xml no cambia)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:resolve dependency:resolve-plugins -B -q

# 3. Copiar código fuente y compilar
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B -q \
    && cp target/shopify-0.0.1-SNAPSHOT.jar /workspace/app.jar

# ------------------------------------------------------------------------------
# STAGE 2: RUNTIME — Imagen mínima de producción con JRE 17
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Metadatos de la imagen
LABEL maintainer="ShopifyClon Team" \
      description="ShopifyClon API Core - Spring Boot 3.3.4" \
      version="0.0.1-SNAPSHOT"

# Crear usuario sin privilegios para ejecutar la aplicación
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Directorio de trabajo
WORKDIR /app

# Copiar únicamente el JAR desde el stage de build
COPY --from=build /workspace/app.jar app.jar

# Asignar propiedad al usuario no-root
RUN chown appuser:appgroup app.jar

# Cambiar a usuario sin privilegios
USER appuser

# Puerto de la aplicación (server.port=8080)
EXPOSE 8080

# JVM: terminar el proceso inmediatamente si se queda sin memoria.
# Configuraciones adicionales de JVM (heap, GC, etc.) deben inyectarse
# externamente via JAVA_TOOL_OPTIONS desde la infraestructura.
# Ejemplo: docker run -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" ...
ENV JAVA_OPTS="-XX:+ExitOnOutOfMemoryError"

# Exec form para recibir señales POSIX correctamente (SIGTERM → graceful shutdown)
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
