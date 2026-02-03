FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy maven wrapper and parent pom
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy full module directories so Maven can resolve the multi-module project during Docker build
COPY common ./common
COPY mcp-server ./mcp-server
COPY agents ./agents

# Build only the kafka-mcp-server module but allow dependent modules to be built (-am)
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl mcp-server/kafka-mcp-server -am

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/mcp-server/kafka-mcp-server/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
