# syntax=docker/dockerfile:1
# =====================================================================
# Daytesk server — Ktor/Netty sobre Kotlin JVM
# Multi-stage: build con JDK 17, run con JRE 17 liviano.
# Build context esperado: la RAÍZ del repo (Render así lo entrega).
# =====================================================================

# ----- 1. Stage de build --------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 1a) Manifiestos + wrapper primero → maximiza el cache de capas de Gradle.
#     Un cambio sólo en código .kt NO invalida la resolución de deps.
COPY settings.gradle.kts build.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
COPY app/build.gradle.kts   ./app/build.gradle.kts
COPY server/build.gradle.kts ./server/build.gradle.kts

RUN chmod +x ./gradlew

# 1b) Pre-warm del cache de dependencias (best-effort: tolera fallos parciales).
RUN ./gradlew :server:dependencies --no-daemon || true

# 1c) Ahora sí, todo el código fuente.
COPY . .

# 1d) Compilamos y dejamos lista la "install distribution" del server.
#     Produce: server/build/install/daytesk-server/
#              ├─ bin/daytesk-server    (script wrapper)
#              └─ lib/*.jar             (classpath completo)
RUN ./gradlew :server:installDist --no-daemon \
 && ls -la /workspace/server/build/install/daytesk-server

# ----- 2. Stage de runtime ------------------------------------------------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# 2a) Sólo la distribución del server, nada más del repo.
COPY --from=build /workspace/server/build/install/daytesk-server /app/server

# 2b) Variables de entorno operativas.
#     - PORT: Render la inyecta automáticamente en cada deploy.
#     - JAVA_TOOL_OPTIONS: puerta abierta para tunear memoria/GClog vía ENV.
#     - DATABASE_URL y JWT_SECRET: configurarlas en el panel de Render.
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS=""

EXPOSE 8080

# 2c) Arranque directo con `java -cp` → no dependemos del wrapper de Gradle,
#     ni del nombre de carpeta de `applicationName` (más portable).
#     El entry del server es com.nuitcode.daytesk.server.ApplicationKt
#     (ver server/build.gradle.kts → application { mainClass.set(...) }).
CMD ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS -cp '/app/server/lib/*' com.nuitcode.daytesk.server.ApplicationKt"]

# HEALTHCHECK: Render hace su propio ping a /health, pero este queda como
# red de seguridad si lo deployan en otra plataforma (docker compose, k8s).
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD ["sh", "-c", "wget -qO- http://127.0.0.1:${PORT:-8080}/health || exit 1"]
