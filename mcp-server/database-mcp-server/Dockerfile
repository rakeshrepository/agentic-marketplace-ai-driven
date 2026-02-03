# Build stage
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy parent pom
COPY pom.xml ./
COPY mvnw ./
COPY .mvn ./.mvn

# Copy common module
COPY common ./common

# Copy all mcp-server modules
COPY mcp-server ./mcp-server

# Copy all agent modules
COPY agents ./agents

# Build the application
RUN chmod +x mvnw && ./mvnw clean package -pl mcp-server/database-mcp-server -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/mcp-server/database-mcp-server/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
