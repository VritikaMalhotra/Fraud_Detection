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
# First install parent POM, then common-models, then build alerts-service
RUN mvn install -N -DskipTests && \
    mvn install -pl libs/common-models -DskipTests && \
    cd services/alerts-service && \
    mvn clean package spring-boot:repackage -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage (built from /app/services/alerts-service)
COPY --from=build /app/services/alerts-service/target/*.jar app.jar

# Expose port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
