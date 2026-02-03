FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy maven wrapper and parent pom
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy modules (copy full directories so multi-module build can resolve all child modules)
COPY common ./common
COPY mcp-server ./mcp-server
COPY agents ./agents
COPY agent-registry-service ./agent-registry-service

# Build the agent (only package the agent module but allow -am to build required modules)
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl agents/database-management-agent -am

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/agents/database-management-agent/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
