# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------------------------------
# Build. Dependencies are resolved before the sources are copied, so editing a source file does not
# invalidate the dependency layer.
# ---------------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---------------------------------------------------------------------------------------------------
# Extract. The layered jar splits into dependencies, which rarely change, and application classes,
# which change on every commit.
# ---------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
COPY --from=build /build/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------------------------------
# Runtime. No secret is ever baked into an image layer: every credential arrives as an environment
# variable at run time.
# ---------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S iguide && adduser -S iguide -G iguide
USER iguide:iguide

COPY --from=extract --chown=iguide:iguide /extract/extracted/dependencies/ ./
COPY --from=extract --chown=iguide:iguide /extract/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=iguide:iguide /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=iguide:iguide /extract/extracted/application/ ./

EXPOSE 8080

# The same endpoint the orchestrator probes. It is the one operational endpoint reachable without
# authentication, which is why it can be used here at all.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:8080/iguide/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
