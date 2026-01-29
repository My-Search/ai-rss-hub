FROM maven:3.9.6-eclipse-temurin-8 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package


FROM eclipse-temurin:8-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/ai-rss-hub-1.0.0.jar app.jar

RUN mkdir -p /app/data \
    && apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
