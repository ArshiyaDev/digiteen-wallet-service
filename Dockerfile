FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp clean verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 wallet
COPY --from=build /workspace/target/digiteen-wallet-service-*.jar app.jar
USER wallet
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
