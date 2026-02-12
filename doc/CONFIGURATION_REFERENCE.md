# Configuration Reference Guide

This document provides a comprehensive reference for all configuration values extracted from hardcoded values in the application.

## 📋 Overview

All hardcoded values have been extracted to configuration files to make the application more flexible and environment-specific. Configuration is now centralized in:

1. **Kafka MCP Server**: `mcp-server/kafka-mcp-server/src/main/resources/application.yml`
2. **Agent Registry Service**: `agent-registry-service/src/main/resources/application.yml`
3. **Web Application**: `web-app/.env`

## 🔧 Kafka MCP Server Configuration

### File: `mcp-server/kafka-mcp-server/src/main/resources/application.yml`

#### Kafka Configuration
```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  admin:
    request-timeout-ms: ${KAFKA_ADMIN_TIMEOUT:10000}
    default-partitions: ${KAFKA_DEFAULT_PARTITIONS:1}
    default-replication-factor: ${KAFKA_DEFAULT_REPLICATION:1}
```

**Environment Variables:**
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses (default: `localhost:9092`)
- `KAFKA_ADMIN_TIMEOUT` - Admin client request timeout in milliseconds (default: `10000`)
- `KAFKA_DEFAULT_PARTITIONS` - Default partition count for new topics (default: `1`)
- `KAFKA_DEFAULT_REPLICATION` - Default replication factor (default: `1`)

#### HTTP Server Configuration
```yaml
http:
  server:
    port: ${HTTP_SERVER_PORT:8081}
    shutdown-timeout-seconds: ${HTTP_SERVER_SHUTDOWN_TIMEOUT:5}
    backlog: ${HTTP_SERVER_BACKLOG:0}
  cors:
    enabled: ${HTTP_CORS_ENABLED:true}
    allowed-origins: ${HTTP_CORS_ALLOWED_ORIGINS:*}
    allowed-methods: ${HTTP_CORS_ALLOWED_METHODS:POST, OPTIONS}
    allowed-headers: ${HTTP_CORS_ALLOWED_HEADERS:Content-Type}
  response:
    default-content-type: ${HTTP_RESPONSE_CONTENT_TYPE:application/json}
    status-codes:
      success: ${HTTP_STATUS_SUCCESS:200}
      no-content: ${HTTP_STATUS_NO_CONTENT:204}
      method-not-allowed: ${HTTP_STATUS_METHOD_NOT_ALLOWED:405}
      internal-error: ${HTTP_STATUS_INTERNAL_ERROR:500}
```

**Environment Variables:**
- `HTTP_SERVER_PORT` - HTTP server port (default: `8081`)
- `HTTP_SERVER_SHUTDOWN_TIMEOUT` - Graceful shutdown timeout in seconds (default: `5`)
- `HTTP_SERVER_BACKLOG` - HTTP server connection backlog (default: `0`)
- `HTTP_CORS_ENABLED` - Enable CORS headers (default: `true`)
- `HTTP_CORS_ALLOWED_ORIGINS` - Allowed CORS origins (default: `*`)
- `HTTP_CORS_ALLOWED_METHODS` - Allowed HTTP methods (default: `POST, OPTIONS`)
- `HTTP_CORS_ALLOWED_HEADERS` - Allowed HTTP headers (default: `Content-Type`)
- `HTTP_RESPONSE_CONTENT_TYPE` - Default response content type (default: `application/json`)
- `HTTP_STATUS_SUCCESS` - HTTP success status code (default: `200`)
- `HTTP_STATUS_NO_CONTENT` - HTTP no content status code (default: `204`)
- `HTTP_STATUS_METHOD_NOT_ALLOWED` - HTTP method not allowed status (default: `405`)
- `HTTP_STATUS_INTERNAL_ERROR` - HTTP internal error status (default: `500`)

## 🗄️ Agent Registry Service Configuration

### File: `agent-registry-service/src/main/resources/application.yml`

#### Data Initialization Configuration
```yaml
agent-registry:
  data-init:
    min-categories: ${REGISTRY_MIN_CATEGORIES:9}
    min-agents: ${REGISTRY_MIN_AGENTS:27}
    reinitialize-on-partial-data: ${REGISTRY_REINIT_PARTIAL:true}
  category-colors:
    infrastructure: ${CATEGORY_COLOR_INFRASTRUCTURE:#FF6B6B}
    devops: ${CATEGORY_COLOR_DEVOPS:#326CE5}
    developer-tools: ${CATEGORY_COLOR_DEVELOPER_TOOLS:#F7DF1E}
    cicd-automation: ${CATEGORY_COLOR_CICD:#2088FF}
    data-analytics: ${CATEGORY_COLOR_ANALYTICS:#9B59B6}
    security-monitoring: ${CATEGORY_COLOR_SECURITY:#E74C3C}
    ai-intelligent: ${CATEGORY_COLOR_AI:#3498DB}
    integration-hub: ${CATEGORY_COLOR_INTEGRATION:#1ABC9C}
    custom-solutions: ${CATEGORY_COLOR_CUSTOM:#95A5A6}
```

**Environment Variables:**
- `REGISTRY_MIN_CATEGORIES` - Minimum expected categories (default: `9`)
- `REGISTRY_MIN_AGENTS` - Minimum expected agents (default: `27`)
- `REGISTRY_REINIT_PARTIAL` - Reinitialize if partial data found (default: `true`)
- `CATEGORY_COLOR_INFRASTRUCTURE` - Color for infrastructure category (default: `#FF6B6B`)
- `CATEGORY_COLOR_DEVOPS` - Color for devops category (default: `#326CE5`)
- `CATEGORY_COLOR_DEVELOPER_TOOLS` - Color for developer tools (default: `#F7DF1E`)
- `CATEGORY_COLOR_CICD` - Color for CI/CD category (default: `#2088FF`)
- `CATEGORY_COLOR_ANALYTICS` - Color for analytics category (default: `#9B59B6`)
- `CATEGORY_COLOR_SECURITY` - Color for security category (default: `#E74C3C`)
- `CATEGORY_COLOR_AI` - Color for AI category (default: `#3498DB`)
- `CATEGORY_COLOR_INTEGRATION` - Color for integration hub (default: `#1ABC9C`)
- `CATEGORY_COLOR_CUSTOM` - Color for custom solutions (default: `#95A5A6`)

#### Database Constraints Configuration
```yaml
database:
  constraints:
    agent:
      status-length: ${DB_AGENT_STATUS_LENGTH:50}
      endpoint-url-length: ${DB_AGENT_ENDPOINT_LENGTH:500}
      icon-length: ${DB_AGENT_ICON_LENGTH:50}
      color-length: ${DB_AGENT_COLOR_LENGTH:50}
```

**Environment Variables:**
- `DB_AGENT_STATUS_LENGTH` - Max length for agent status field (default: `50`)
- `DB_AGENT_ENDPOINT_LENGTH` - Max length for endpoint URL (default: `500`)
- `DB_AGENT_ICON_LENGTH` - Max length for icon field (default: `50`)
- `DB_AGENT_COLOR_LENGTH` - Max length for color field (default: `50`)

**Note:** These are currently informational. To change column lengths, you'll need to update the `@Column` annotations in the `Agent.java` entity class and create a database migration.

## 🌐 Web Application Configuration

### File: `web-app/.env`

```env
# API Configuration
VITE_API_BASE_URL=
VITE_AGENT_REGISTRY_URL=http://localhost:8090

# MCP Server URLs
VITE_KAFKA_MCP_URL=/mcp/kafka
VITE_DATABASE_MCP_URL=/mcp/database

# Ollama Configuration (LLM)
VITE_OLLAMA_URL=/ollama
VITE_OLLAMA_MODEL=llama3.2:latest

# AI Service Configuration
VITE_OLLAMA_TEMPERATURE=0.7
VITE_OLLAMA_NUM_PREDICT=500

# MCP Client Configuration
VITE_MCP_REQUEST_ID_START=1
```

**Configuration Variables:**

### API Configuration
- `VITE_API_BASE_URL` - Base URL for API calls (optional)
- `VITE_AGENT_REGISTRY_URL` - Agent Registry service URL (default: `http://localhost:8090`)

### MCP Server URLs
- `VITE_KAFKA_MCP_URL` - Kafka MCP server URL (default: `/mcp/kafka`)
- `VITE_DATABASE_MCP_URL` - Database MCP server URL (default: `/mcp/database`)

### Ollama/LLM Configuration
- `VITE_OLLAMA_URL` - Ollama service URL (default: `/ollama`)
- `VITE_OLLAMA_MODEL` - LLM model name (default: `llama3.2:latest`)
- `VITE_OLLAMA_TEMPERATURE` - LLM temperature (creativity) setting (default: `0.7`, range: 0.0-1.0)
- `VITE_OLLAMA_NUM_PREDICT` - Maximum tokens to predict (default: `500`)

### MCP Client Configuration
- `VITE_MCP_REQUEST_ID_START` - Starting request ID for JSON-RPC calls (default: `1`)

## 🚀 Usage Examples

### Production Deployment

For production, you can override defaults using environment variables:

#### Docker Compose Example
```yaml
services:
  kafka-mcp-server:
    image: kafka-mcp-server:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka-prod:9092
      KAFKA_ADMIN_TIMEOUT: 30000
      HTTP_SERVER_PORT: 8081
      HTTP_CORS_ALLOWED_ORIGINS: https://yourdomain.com
      
  agent-registry:
    image: agent-registry:latest
    environment:
      REGISTRY_MIN_CATEGORIES: 9
      REGISTRY_MIN_AGENTS: 50
      CATEGORY_COLOR_INFRASTRUCTURE: "#FF0000"
      
  web-app:
    image: web-app:latest
    environment:
      VITE_AGENT_REGISTRY_URL: https://api.yourdomain.com
      VITE_OLLAMA_MODEL: llama3.3:latest
      VITE_OLLAMA_TEMPERATURE: 0.8
```

#### Kubernetes ConfigMap Example
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: kafka-mcp-config
data:
  KAFKA_BOOTSTRAP_SERVERS: "kafka-svc:9092"
  KAFKA_ADMIN_TIMEOUT: "30000"
  HTTP_SERVER_PORT: "8081"
  HTTP_CORS_ALLOWED_ORIGINS: "*"
```

### Development Customization

For local development, simply edit the `.env` file in the web-app directory:

```bash
# Increase LLM creativity
VITE_OLLAMA_TEMPERATURE=0.9

# Use a different model
VITE_OLLAMA_MODEL=llama3:8b

# Longer responses
VITE_OLLAMA_NUM_PREDICT=1000
```

## 📝 Migration Summary

### What Changed

All hardcoded values have been replaced with configuration-driven values:

1. **KafkaAdminService.java**: Request timeout (`10000ms`) → `KAFKA_ADMIN_TIMEOUT`
2. **SimpleHttpMcpServer.java**: 
   - Server port (`8081`) → `HTTP_SERVER_PORT`
   - Shutdown timeout (`5`) → `HTTP_SERVER_SHUTDOWN_TIMEOUT`
   - HTTP status codes → `HTTP_STATUS_*` variables
   - CORS settings → `HTTP_CORS_*` variables
3. **KafkaMcpServerApplication.java**: 
   - Bootstrap servers (`localhost:9092`) → `KAFKA_BOOTSTRAP_SERVERS`
   - Removed hardcoded constants
4. **DataInitializer.java**: 
   - Category counts (`9`, `27`) → `REGISTRY_MIN_CATEGORIES`, `REGISTRY_MIN_AGENTS`
   - Category colors → `CATEGORY_COLOR_*` variables
5. **aiService.ts**: 
   - Temperature (`0.7`) → `VITE_OLLAMA_TEMPERATURE`
   - Token limit (`500`) → `VITE_OLLAMA_NUM_PREDICT`
6. **mcpClient.ts**: 
   - Request ID start (`1`) → `VITE_MCP_REQUEST_ID_START`

### Benefits

✅ **Flexibility**: Change configuration without recompiling code  
✅ **Environment-specific**: Different configs for dev/staging/prod  
✅ **Documentation**: All settings in one place with clear defaults  
✅ **Best Practices**: Follows 12-factor app methodology  
✅ **Maintainability**: Easier to understand and modify  
✅ **Security**: Sensitive values can be injected at runtime  

## 🔍 Verification

After making configuration changes, verify your setup:

### Kafka MCP Server
```bash
# Check if server starts with custom config
docker logs kafka-mcp-server 2>&1 | grep "Kafka AdminClient initialized"
docker logs kafka-mcp-server 2>&1 | grep "MCP HTTP Server started"
```

### Agent Registry
```bash
# Verify category initialization
docker logs agent-registry 2>&1 | grep "Database initialization completed"
```

### Web App
```bash
# Check browser console for configuration values
# Look for: [AI Service] Calling Ollama at: ...
```

## 🆘 Troubleshooting

### Issue: Server won't start
**Solution**: Check environment variables are properly set. Use defaults by not setting the variable.

### Issue: Wrong colors in UI
**Solution**: Verify `CATEGORY_COLOR_*` environment variables match hex color format `#RRGGBB`

### Issue: LLM responses too short/long
**Solution**: Adjust `VITE_OLLAMA_NUM_PREDICT` value (100-2000 recommended range)

### Issue: LLM responses too creative/boring
**Solution**: Adjust `VITE_OLLAMA_TEMPERATURE` (0.1 = deterministic, 1.0 = creative)

## 📚 Additional Resources

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Vite Environment Variables](https://vitejs.dev/guide/env-and-mode.html)
- [12-Factor App Configuration](https://12factor.net/config)
- [Ollama API Documentation](https://github.com/ollama/ollama/blob/main/docs/api.md)

---

**Last Updated**: December 2024  
**Version**: 1.0.0
