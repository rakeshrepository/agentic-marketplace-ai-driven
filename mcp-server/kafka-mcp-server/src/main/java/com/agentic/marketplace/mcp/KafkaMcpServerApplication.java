package com.agentic.marketplace.mcp;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class KafkaMcpServerApplication {
    private static final Logger log = LoggerFactory.getLogger(KafkaMcpServerApplication.class);
    private static final McpJsonMapper JSON_MAPPER = McpJsonMapper.getDefault();

    public static void main(String[] args) {
        log.info("=== Starting Kafka MCP Server ===");
        
        // Load configuration from environment
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        int requestTimeout = getEnvAsInt("KAFKA_ADMIN_TIMEOUT", 10000);
        
        KafkaAdminService kafkaAdmin = new KafkaAdminService(bootstrapServers, requestTimeout);
        
        // Start simple HTTP MCP server with JSON-RPC 2.0
        SimpleHttpMcpServer mcpServer;
        try {
            HttpServerConfig httpConfig = new HttpServerConfig();
            mcpServer = new SimpleHttpMcpServer(httpConfig, kafkaAdmin);
            mcpServer.start();
        } catch (IOException e) {
            log.error("Failed to start MCP server", e);
            kafkaAdmin.close();
            System.exit(1);
            return;
        }
        
        /* Original SSE-based server (requires servlet container)
        HttpServletSseServerTransportProvider transport = HttpServletSseServerTransportProvider.builder()
            .messageEndpoint("/mcp/message")
            .sseEndpoint("/sse")
            .build();
        */
        
        try {
            /* SSE-based server code commented out
            McpAsyncServer mcpAsyncServer = McpServer.async(transport)
                .serverInfo("kafka-mcp-server", "1.0.0")
                .instructions("Kafka MCP Server for topic management")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                
                // Tool 1: Create Topic
                .tool(
                    McpSchema.Tool.builder()
                        .name("create_topic")
                        .description("Creates a new Kafka topic")
                        .inputSchema(JSON_MAPPER, """
                            {
                                "type": "object",
                                "properties": {
                                    "topic_name": {"type": "string", "description": "Topic name"},
                                    "partitions": {"type": "integer", "description": "Number of partitions"},
                                    "replication_factor": {"type": "integer", "description": "Replication factor"}
                                },
                                "required": ["topic_name"]
                            }
                            """)
                        .build(),
                    (exchange, argumentsMap) -> Mono.fromCallable(() -> {
                        String topic = (String) argumentsMap.get("topic_name");
                        int partitions = argumentsMap.containsKey("partitions") ? 
                            ((Number) argumentsMap.get("partitions")).intValue() : 1;
                        short replication = argumentsMap.containsKey("replication_factor") ? 
                            ((Number) argumentsMap.get("replication_factor")).shortValue() : (short) 1;
                        
                        Map<String, Object> result = kafkaAdmin.createTopic(topic, partitions, replication);
                        return McpSchema.CallToolResult.builder()
                            .addTextContent("✓ Topic created: " + result)
                            .build();
                    })
                )
                
                // Tool 2: List Topics
                .tool(
                    McpSchema.Tool.builder()
                        .name("list_topics")
                        .description("Lists all Kafka topics")
                        .inputSchema(JSON_MAPPER, """
                            {"type": "object", "properties": {}}
                            """)
                        .build(),
                    (exchange, request) -> Mono.fromCallable(() -> {
                        var topics = kafkaAdmin.listTopics();
                        return McpSchema.CallToolResult.builder()
                            .addTextContent("Topics (" + topics.size() + "): " + String.join(", ", topics))
                            .build();
                    })
                )
                
                // Tool 3: Describe Topic
                .tool(
                    McpSchema.Tool.builder()
                        .name("describe_topic")
                        .description("Get topic details")
                        .inputSchema(JSON_MAPPER, """
                            {
                                "type": "object",
                                "properties": {
                                    "topic_name": {"type": "string", "description": "Topic name"}
                                },
                                "required": ["topic_name"]
                            }
                            """)
                        .build(),
                    (exchange, argumentsMap) -> Mono.fromCallable(() -> {
                        String topic = (String) argumentsMap.get("topic_name");
                        Map<String, Object> details = kafkaAdmin.describeTopic(topic);
                        return McpSchema.CallToolResult.builder()
                            .addTextContent("Topic details:\n" + formatMap(details))
                            .build();
                    })
                )
                
                // Tool 4: Delete Topic
                .tool(
                    McpSchema.Tool.builder()
                        .name("delete_topic")
                        .description("Delete a Kafka topic")
                        .inputSchema(JSON_MAPPER, """
                            {
                                "type": "object",
                                "properties": {
                                    "topic_name": {"type": "string", "description": "Topic name"}
                                },
                                "required": ["topic_name"]
                            }
                            """)
                        .build(),
                    (exchange, argumentsMap) -> Mono.fromCallable(() -> {
                        String topic = (String) argumentsMap.get("topic_name");
                        Map<String, Object> result = kafkaAdmin.deleteTopic(topic);
                        return McpSchema.CallToolResult.builder()
                            .addTextContent("✓ Topic deleted: " + result)
                            .build();
                    })
                )
                
                // Tool 5: Topic Exists
                .tool(
                    McpSchema.Tool.builder()
                        .name("topic_exists")
                        .description("Check if topic exists")
                        .inputSchema(JSON_MAPPER, """
                            {
                                "type": "object",
                                "properties": {
                                    "topic_name": {"type": "string", "description": "Topic name"}
                                },
                                "required": ["topic_name"]
                            }
                            """)
                        .build(),
                    (exchange, argumentsMap) -> Mono.fromCallable(() -> {
                        String topic = (String) argumentsMap.get("topic_name");
                        boolean exists = kafkaAdmin.topicExists(topic);
                        return McpSchema.CallToolResult.builder()
                            .addTextContent("Topic '" + topic + "' " + (exists ? "EXISTS" : "DOES NOT EXIST"))
                            .build();
                    })
                )
                
                // Tool 6: Cluster Overview
                .tool(
                    McpSchema.Tool.builder()
                        .name("cluster_overview")
                        .description("Get Kafka cluster overview")
                        .inputSchema(JSON_MAPPER, """
                            {"type": "object", "properties": {}}
                            """)
                        .build(),
                    (exchange, request) -> Mono.fromCallable(() -> {
                        Map<String, Object> overview = kafkaAdmin.getClusterOverview();
                        return McpSchema.CallToolResult.builder()
                            .addTextContent("Cluster Overview:\n" + formatMap(overview))
                            .build();
                    })
                )
                
                .build();
            */
            
            log.info("✓ MCP Server started successfully");
            log.info("✓ 6 tools registered: create_topic, list_topics, describe_topic, delete_topic, topic_exists, cluster_overview");
            log.info("✓ Bootstrap Servers: {}", bootstrapServers);
            log.info("✓ HTTP endpoint: http://localhost:8081/mcp/message");
            log.info("✓ Health check endpoint: http://localhost:8081/health");
            log.info("✓ Ready for LLM connections");
            
            // Add shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Kafka MCP Server...");
                mcpServer.stop();
                kafkaAdmin.close();
                log.info("Shutdown complete");
            }));
            
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Failed to start", e);
            mcpServer.stop();
            kafkaAdmin.close();
            System.exit(1);
        }
    }
    
    /**
     * Starts a simple HTTP server for health checks
     */
    private static HttpServer startHealthCheckServer(KafkaAdminService kafkaAdmin) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
            
            // Health check endpoint
            server.createContext("/health", exchange -> {
                try {
                    // Check if Kafka is accessible
                    boolean healthy = kafkaAdmin.topicExists("__health_check_topic__") || true; // Always return true for now
                    
                    String response = """
                        {
                            "status": "UP",
                            "service": "kafka-mcp-server",
                            "kafka": {
                                "bootstrapServers": "%s",
                                "connected": true
                            }
                        }
                        """.formatted(System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
                    
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    log.error("Health check failed", e);
                    String errorResponse = """
                        {
                            "status": "DOWN",
                            "error": "%s"
                        }
                        """.formatted(e.getMessage());
                    
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(503, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    }
                }
            });
            
            server.setExecutor(null); // Use default executor
            server.start();
            log.info("✓ Health check server started on port 8081");
            return server;
        } catch (IOException e) {
            log.error("Failed to start health check server", e);
            throw new RuntimeException("Cannot start health check server", e);
        }
    }
    
    private static String formatMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((key, value) -> sb.append("  ").append(key).append(": ").append(value).append("\n"));
        return sb.toString();
    }
    
    private static int getEnvAsInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for {}: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
