# Multi-stage build for alerts-service
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy parent pom and common-models first for better caching
COPY pom.xml ./
COPY libs/common-models/pom.xml ./libs/common-models/
COPY libs/common-models/src ./libs/common-models/src

# Copy all service pom.xml files (Maven needs them for the reactor build)
COPY services/ingest-api/pom.xml ./services/ingest-api/
COPY services/fraud-service/pom.xml ./services/fraud-service/
COPY services/alerts-service/pom.xml ./services/alerts-service/

# Copy only alerts-service source code
COPY services/alerts-service/src ./services/alerts-service/src

# Build the application
RUN mvn clean install -pl libs/common-models -DskipTests
RUN mvn package spring-boot:repackage -pl services/alerts-service -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/services/alerts-service/target/alerts-service-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
