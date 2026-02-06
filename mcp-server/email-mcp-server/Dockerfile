# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy parent POM
COPY pom.xml .

# Create stub pom.xml files for modules we don't need (with proper parent relativePath)
RUN mkdir -p mcp-server/kafka-mcp-server && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.agentic.marketplace</groupId><artifactId>agentic-marketplace-ai-driven</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent><artifactId>kafka-mcp-server</artifactId><packaging>pom</packaging></project>' > mcp-server/kafka-mcp-server/pom.xml && \
    mkdir -p mcp-server/database-mcp-server && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.agentic.marketplace</groupId><artifactId>agentic-marketplace-ai-driven</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent><artifactId>database-mcp-server</artifactId><packaging>pom</packaging></project>' > mcp-server/database-mcp-server/pom.xml && \
    mkdir -p agents/topic-management-agent && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.agentic.marketplace</groupId><artifactId>agentic-marketplace-ai-driven</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent><artifactId>topic-management-agent</artifactId><packaging>pom</packaging></project>' > agents/topic-management-agent/pom.xml && \
    mkdir -p agents/database-management-agent && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.agentic.marketplace</groupId><artifactId>agentic-marketplace-ai-driven</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent><artifactId>database-management-agent</artifactId><packaging>pom</packaging></project>' > agents/database-management-agent/pom.xml && \
    mkdir -p agent-registry-service && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.agentic.marketplace</groupId><artifactId>agentic-marketplace-ai-driven</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent><artifactId>agent-registry-service</artifactId><packaging>pom</packaging></project>' > agent-registry-service/pom.xml

# Copy agent-sdk (required dependency)
COPY common/agent-sdk/pom.xml common/agent-sdk/pom.xml
COPY common/agent-sdk/src common/agent-sdk/src

# Copy email-mcp-server module
COPY mcp-server/email-mcp-server/pom.xml mcp-server/email-mcp-server/pom.xml
COPY mcp-server/email-mcp-server/src mcp-server/email-mcp-server/src

# Build only the email-mcp-server and its dependencies
RUN mvn clean package -pl :email-mcp-server -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/mcp-server/email-mcp-server/target/*.jar app.jar

# Expose application port
EXPOSE 8084

# Health check (using java instead of wget)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD java -cp app.jar org.springframework.boot.loader.JarLauncher --health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
