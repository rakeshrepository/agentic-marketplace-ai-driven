# Email MCP Server - Implementation Summary

## Overview
The Email MCP Server is a Model Context Protocol (MCP) server that provides email sending capabilities for the Agentic Marketplace. Following the MCP architecture pattern, it acts as a specialized tool server that agents can use to send email notifications.

## Architecture

### MCP Pattern
- **Email MCP Server** (Port 8084): Specialized email sending service
- **Topic Management Agent**: Can use this server to send Kafka credentials after topic creation
- **Separation of Concerns**: Email logic is isolated from agent intelligence

### Technology Stack
- **Framework**: Spring Boot 3.2.2
- **Email**: Spring Mail with JavaMailSender
- **Templates**: Thymeleaf for HTML email templates
- **SMTP Testing**: MailHog (lightweight SMTP server with web UI)
- **Retry Logic**: Spring Retry with configurable attempts
- **Validation**: Jakarta Bean Validation
- **Build**: Maven multi-module project

## Components Created

### 1. Core Application Files
```
mcp-server/email-mcp-server/
├── pom.xml                                    # Maven configuration
├── Dockerfile                                 # Multi-stage Docker build
└── src/main/
    ├── java/com/agentic/marketplace/mcp/
    │   ├── EmailMcpServerApplication.java    # Spring Boot entry point
    │   ├── config/
    │   │   └── EmailConfig.java              # Configuration properties
    │   ├── controller/
    │   │   └── EmailController.java          # REST API endpoints
    │   ├── model/
    │   │   ├── EmailRequest.java             # Request model with validation
    │   │   └── EmailResponse.java            # Response model
    │   └── service/
    │       └── EmailService.java             # Email sending logic
    └── resources/
        ├── application.yml                    # SMTP and app configuration
        └── templates/
            ├── kafka-credentials.html         # Kafka credentials email template
            └── plain-text.html                # Generic text email template
```

### 2. Key Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Boot Mail -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    
    <!-- Spring Retry -->
    <dependency>
        <groupId>org.springframework.retry</groupId>
        <artifactId>spring-retry</artifactId>
    </dependency>
    
    <!-- Thymeleaf Templates -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

### 3. Configuration (application.yml)
```yaml
server:
  port: 8084

spring:
  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:1025}
    username: ${SPRING_MAIL_USERNAME:}
    password: ${SPRING_MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
  thymeleaf:
    mode: HTML
    encoding: UTF-8
    cache: false

email:
  from:
    address: ${EMAIL_FROM_ADDRESS:noreply@agentic-marketplace.local}
    name: ${EMAIL_FROM_NAME:Agentic Marketplace}
  rate-limit:
    enabled: true
    permits-per-minute: 10
  retry:
    max-attempts: 3
    delay-ms: 1000
```

### 4. Docker Compose Integration
```yaml
# MailHog - SMTP testing server
mailhog:
  image: mailhog/mailhog:v1.0.1
  container_name: mailhog
  ports:
    - "1025:1025"  # SMTP port
    - "8025:8025"  # Web UI port

# Email MCP Server
email-mcp-server:
  build:
    context: .
    dockerfile: mcp-server/email-mcp-server/Dockerfile
  container_name: email-mcp-server
  depends_on:
    mailhog:
      condition: service_healthy
  ports:
    - "8084:8084"
  environment:
    SPRING_MAIL_HOST: mailhog
    SPRING_MAIL_PORT: 1025
```

## API Endpoints

### 1. Send Email
```bash
POST /api/emails/send
Content-Type: application/json

{
  "to": "user@example.com",
  "subject": "Kafka Topic Created",
  "templateName": "kafka-credentials",
  "templateData": {
    "topicName": "orders",
    "partitions": 3,
    "replicationFactor": 1,
    "bootstrapServers": "localhost:9092"
  },
  "cc": ["manager@example.com"],
  "bcc": [],
  "replyTo": "support@agentic-marketplace.local"
}
```

**Response**:
```json
{
  "success": true,
  "message": "Email sent successfully",
  "emailId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1707225600000
}
```

### 2. Health Check
```bash
GET /api/emails/health
```

**Response**:
```json
{
  "status": "UP",
  "service": "email-mcp-server",
  "timestamp": 1707225600000
}
```

### 3. List Templates
```bash
GET /api/emails/templates
```

**Response**:
```json
{
  "available": ["kafka-credentials", "plain-text"],
  "description": "Available Thymeleaf email templates"
}
```

## Email Templates

### Kafka Credentials Template
Location: `src/main/resources/templates/kafka-credentials.html`

**Features**:
- Professional HTML design with inline CSS
- Topic details section (name, partitions, replication factor)
- Bootstrap servers information
- Java producer example with syntax highlighting
- Python producer example with syntax highlighting
- Security warning about credential storage
- Next steps and best practices
- Responsive design for mobile devices

**Template Variables**:
- `topicName`: Name of the created topic
- `partitions`: Number of partitions
- `replicationFactor`: Replication factor
- `bootstrapServers`: Kafka broker connection string

### Plain Text Template
Location: `src/main/resources/templates/plain-text.html`

**Features**:
- Simple, clean design
- Customizable subject and content
- Generic template for any text-based email

**Template Variables**:
- `subject`: Email subject
- `content`: Email body content

## Features

### 1. Retry Mechanism
- Automatic retry on failure using Spring Retry
- Configurable max attempts (default: 3)
- Configurable delay between retries (default: 1000ms)
- Exponential backoff support

### 2. Rate Limiting
- Configurable permits per minute (default: 10)
- Protects against email spam
- Can be enabled/disabled via configuration

### 3. Template Engine
- Thymeleaf for dynamic HTML emails
- Support for data binding
- Conditional rendering
- Reusable components

### 4. Validation
- Jakarta Bean Validation annotations
- Email format validation
- Required field validation
- Custom validation messages

### 5. Error Handling
- Comprehensive error responses
- Validation error details
- SMTP error handling
- Logging for debugging

## Testing with MailHog

### Access MailHog Web UI
```
http://localhost:8025
```

### Features:
- View all sent emails
- Search and filter emails
- Preview HTML and plain text versions
- View email headers
- Delete emails
- REST API for automation

### Test Email Sending
```bash
curl -X POST http://localhost:8084/api/emails/send \
  -H "Content-Type: application/json" \
  -d '{
    "to": "test@example.com",
    "subject": "Test Email",
    "templateName": "kafka-credentials",
    "templateData": {
      "topicName": "orders",
      "partitions": 3,
      "replicationFactor": 1,
      "bootstrapServers": "localhost:9092"
    }
  }'
```

Then check MailHog UI at http://localhost:8025 to see the email.

## Build and Run

### Local Build
```bash
./mvnw clean package -pl mcp-server/email-mcp-server -am
```

### Run Standalone
```bash
java -jar mcp-server/email-mcp-server/target/email-mcp-server-1.0.0-SNAPSHOT.jar
```

### Docker Build
```bash
docker-compose build email-mcp-server
```

### Run with Docker Compose
```bash
docker-compose up mailhog email-mcp-server
```

## Integration with Topic Management Agent

### Next Steps (To Be Implemented)

1. **Update ParsedIntent Model**
   - Add `email` field to capture user's email address
   - Update LLM prompts to extract email from queries

2. **Create EmailMcpService**
   - Service to call Email MCP Server REST API
   - Use WebClient for non-blocking calls
   - Error handling and retry logic

3. **Update AgentOrchestrator**
   - After successful topic creation
   - If email is present in ParsedIntent
   - Call EmailMcpService with topic details

4. **Example Flow**
   ```
   User: "Create topic orders with 3 partitions, send credentials to user@example.com"
   
   1. Agent parses intent → action=create, topicName=orders, partitions=3, email=user@example.com
   2. Agent creates topic via Kafka MCP Server
   3. Agent sends email via Email MCP Server with topic details
   4. User receives professional email with Kafka credentials and sample code
   ```

## Production Considerations

### SMTP Configuration
For production, replace MailHog with a real SMTP server:

```yaml
spring:
  mail:
    host: smtp.gmail.com  # or your SMTP server
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

### Security
1. **Credentials**: Store SMTP credentials in secrets manager (AWS Secrets Manager, HashiCorp Vault)
2. **Rate Limiting**: Adjust based on email provider limits
3. **TLS/SSL**: Enable for encrypted communication
4. **SPF/DKIM**: Configure DNS records for email authentication

### Monitoring
1. **Logging**: Configure log aggregation (ELK, Splunk)
2. **Metrics**: Track email send success/failure rates
3. **Alerts**: Set up alerts for high failure rates
4. **Health Checks**: Monitor service availability

### Scalability
1. **Async Processing**: Consider message queue (SQS, Kafka) for high volume
2. **Load Balancing**: Multiple instances behind load balancer
3. **Circuit Breaker**: Prevent cascading failures
4. **Caching**: Cache templates for better performance

## Summary

The Email MCP Server is now complete with:
- ✅ Spring Boot application setup
- ✅ SMTP integration with Spring Mail
- ✅ Thymeleaf HTML templates (Kafka credentials + plain text)
- ✅ REST API endpoints (send, health, templates)
- ✅ Retry mechanism with Spring Retry
- ✅ Validation with Jakarta Bean Validation
- ✅ MailHog integration for local testing
- ✅ Docker containerization
- ✅ Docker Compose integration
- ✅ Build successful (BUILD SUCCESS)

Next step is to integrate this server with the Topic Management Agent to enable automated email notifications for Kafka topic provisioning.
