# Configuration Reference Guide

This document provides a comprehensive reference for all configuration values for the Kafka MCP Server.

## 📋 Overview

Configuration is centralized in: `mcp-server/kafka-mcp-server/src/main/resources/application.yml`

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

### Benefits

✅ **Flexibility**: Change configuration without recompiling code  
✅ **Environment-specific**: Different configs for dev/staging/prod  
✅ **Documentation**: All settings in one place with clear defaults  
✅ **Best Practices**: Follows 12-factor app methodology  
✅ **Maintainability**: Easier to understand and modify  
✅ **Security**: Sensitive values can be injected at runtime  

## 🔍 Verification

After making configuration changes, verify your setup:

```bash
# Check if server starts with custom config
docker logs kafka-mcp-server 2>&1 | grep "Kafka AdminClient initialized"
docker logs kafka-mcp-server 2>&1 | grep "MCP HTTP Server started"
```

## 🆘 Troubleshooting

### Issue: Server won't start
**Solution**: Check environment variables are properly set. Use defaults by not setting the variable.

### Issue: Connection to Kafka fails
**Solution**: Verify `KAFKA_BOOTSTRAP_SERVERS` points to correct broker address and Kafka is running.

## 📚 Additional Resources

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [12-Factor App Configuration](https://12factor.net/config)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)

---

**Last Updated**: December 2024  
**Version**: 2.0.0
