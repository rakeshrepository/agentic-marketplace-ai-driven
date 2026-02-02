FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy maven wrapper and pom files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY common/agent-sdk/pom.xml common/agent-sdk/
COPY mcp-server/kafka-mcp-server/pom.xml mcp-server/kafka-mcp-server/
COPY agents/topic-management-agent/pom.xml agents/topic-management-agent/

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY common/agent-sdk/src common/agent-sdk/src
COPY mcp-server/kafka-mcp-server/src mcp-server/kafka-mcp-server/src

# Build the project
RUN ./mvnw clean package -DskipTests -pl mcp-server/kafka-mcp-server -am

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/mcp-server/kafka-mcp-server/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
