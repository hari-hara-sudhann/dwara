FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# The healthcheck in docker-compose.yml calls /actuator/health with busybox wget.
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]


# Test runner image: sources are baked in (not bind-mounted) so the container
# cannot write target/ into the working tree. Only reports/ is mounted back.
FROM maven:3.9.6-eclipse-temurin-21-alpine AS test
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp -q -DskipTests dependency:go-offline || true

COPY src ./src
COPY scripts ./scripts
RUN chmod +x scripts/*.sh

CMD ["/bin/sh", "/workspace/scripts/run-tests-in-container.sh"]
