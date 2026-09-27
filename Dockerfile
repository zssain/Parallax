# Multi-stage build for any Parallax Spring Boot service.
# Usage: docker build --build-arg MODULE=application-service -t parallax-application-service .
#
# The builder copies the Maven wrapper and every module POM first so the dependency
# download layer is cached across source changes, then copies sources and packages
# just the requested module (with -am to build its reactor dependencies).

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src

# 1) Wrapper + all POMs first (dependency layer cache).
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./
COPY parallax-engine/pom.xml     parallax-engine/
COPY bureau-contract/pom.xml     bureau-contract/
COPY bureau-mock/pom.xml         bureau-mock/
COPY decision-service/pom.xml    decision-service/
COPY application-service/pom.xml application-service/
COPY assistant-service/pom.xml   assistant-service/
COPY data-generator/pom.xml      data-generator/
COPY account-service/pom.xml     account-service/
RUN chmod +x mvnw && ./mvnw -q -B -pl ${MODULE} -am dependency:go-offline || true

# 2) Sources, then package the module.
COPY . .
RUN ./mvnw -q -B -DskipTests -pl ${MODULE} -am package \
    && mkdir -p /app \
    && cp ${MODULE}/target/*.jar /app/app.jar

FROM eclipse-temurin:21-jre AS runtime
# curl for compose health checks; a non-root user for the runtime.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system parallax \
    && useradd --system --gid parallax --home /app parallax
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
USER parallax
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
