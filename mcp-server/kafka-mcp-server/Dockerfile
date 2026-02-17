# Build stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy maven wrapper and parent pom
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy only the modules that exist in the project
COPY mcp-server ./mcp-server

# Build only the kafka-mcp-server module
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl mcp-server/kafka-mcp-server -am

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Add metadata labels for container management
LABEL org.opencontainers.image.title="Kafka MCP Server"
LABEL org.opencontainers.image.description="Model Context Protocol server for Kafka operations"
LABEL org.opencontainers.image.vendor="Agentic Marketplace"
LABEL org.opencontainers.image.version="1.0.0"
LABEL com.agentic.marketplace.service="kafka-mcp-server"
LABEL com.agentic.marketplace.type="mcp-server"

# Create non-root user for security
RUN groupadd -g 1001 appuser && \
    useradd -r -u 1001 -g appuser appuser

# Copy the fat JAR from build stage (shaded JAR is the final output after replacement)
COPY --from=build /app/mcp-server/kafka-mcp-server/target/kafka-mcp-server-*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose MCP server port
EXPOSE 8081

# Health check endpoint (assumes server will have /health or similar endpoint)
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8081/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Entrypoint with proper signal handling
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
