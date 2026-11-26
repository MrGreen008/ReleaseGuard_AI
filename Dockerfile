FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --uid 10001 releaseguard
WORKDIR /app
COPY docker/healthcheck.sh /app/healthcheck.sh
COPY --from=build /build/target/releaseguard-ai-*.jar /app/releaseguard.jar
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "/app/releaseguard.jar"]
