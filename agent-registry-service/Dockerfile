# Build stage
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and pom files
COPY mvnw .
COPY .mvn ./.mvn
COPY pom.xml .

# Copy only the modules that exist in the project
COPY mcp-server ./mcp-server
COPY agent-registry-service ./agent-registry-service

# Build the application
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl agent-registry-service -am

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/agent-registry-service/target/*.jar app.jar

# Expose port
EXPOSE 8090

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
