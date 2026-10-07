# syntax=docker/dockerfile:1
FROM node:24.19.0-bookworm-slim@sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df AS frontend
WORKDIR /build
COPY frontend/package*.json ./
RUN --mount=type=secret,id=node_cacerts,target=/tmp/ca.pem \
    if [ -f /tmp/ca.pem ]; then export NODE_EXTRA_CA_CERTS=/tmp/ca.pem; fi; npm ci
COPY frontend/ ./
RUN npm run build
FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS backend
WORKDIR /build
COPY backend/pom.xml ./
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml --mount=type=secret,id=java_cacerts,target=/tmp/cacerts \
    if [ -f /tmp/cacerts ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/cacerts"; fi; mvn dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/dist/browser ./src/main/resources/static
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml --mount=type=secret,id=java_cacerts,target=/tmp/cacerts \
    if [ -f /tmp/cacerts ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/cacerts"; fi; mvn package -DskipTests
FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
WORKDIR /app
COPY --from=backend /build/target/printproof-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
