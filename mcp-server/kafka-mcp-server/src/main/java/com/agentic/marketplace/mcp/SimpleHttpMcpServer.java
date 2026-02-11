package com.agentic.marketplace.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Simple HTTP Server for MCP JSON-RPC 2.0 Protocol
 * Provides HTTP POST endpoint for tools/list and tools/call
 */
public class SimpleHttpMcpServer {
    private static final Logger log = LoggerFactory.getLogger(SimpleHttpMcpServer.class);
    private final KafkaAdminService kafkaAdmin;
    private final HttpServer server;

    public SimpleHttpMcpServer(int port, KafkaAdminService kafkaAdmin) throws IOException {
        this.kafkaAdmin = kafkaAdmin;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // MCP Message endpoint
        server.createContext("/mcp/message", this::handleMcpMessage);
        
        // CORS-enabled health endpoint
        server.createContext("/health", this::handleHealth);
        
        server.setExecutor(null);
    }

    public void start() {
        server.start();
        log.info("✓ MCP HTTP Server started on port {}", server.getAddress().getPort());
        log.info("✓ Endpoints: /mcp/message, /health");
    }

    public void stop() {
        server.stop(5);
        log.info("MCP HTTP Server stopped");
    }

    private void handleMcpMessage(HttpExchange exchange) throws IOException {
        // Add CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Read request body
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("MCP Request: {}", requestBody);

            // Parse JSON-RPC request (simple parsing, not production-grade)
            String response = processJsonRpcRequest(requestBody);
            log.debug("MCP Response: {}", response);

            // Send response
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            log.error("Error processing MCP request", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    private String processJsonRpcRequest(String requestBody) {
        try {
            // Simple JSON parsing (you'd use a proper JSON library in production)
            if (requestBody.contains("\"method\":\"tools/list\"") || requestBody.contains("\"method\": \"tools/list\"")) {
                return """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "tools": [
                                {
                                    "name": "create_topic",
                                    "description": "Creates a new Kafka topic",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                            "topic_name": {"type": "string", "description": "Topic name"},
                                            "partitions": {"type": "integer", "description": "Number of partitions", "default": 1},
                                            "replication_factor": {"type": "integer", "description": "Replication factor", "default": 1}
                                        },
                                        "required": ["topic_name"]
                                    }
                                },
                                {
                                    "name": "list_topics",
                                    "description": "Lists all Kafka topics",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {}
                                    }
                                },
                                {
                                    "name": "describe_topic",
                                    "description": "Get detailed information about a topic",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                            "topic_name": {"type": "string", "description": "Topic name"}
                                        },
                                        "required": ["topic_name"]
                                    }
                                },
                                {
                                    "name": "delete_topic",
                                    "description": "Delete a Kafka topic",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                            "topic_name": {"type": "string", "description": "Topic name"}
                                        },
                                        "required": ["topic_name"]
                                    }
                                },
                                {
                                    "name": "topic_exists",
                                    "description": "Check if a topic exists",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                            "topic_name": {"type": "string", "description": "Topic name"}
                                        },
                                        "required": ["topic_name"]
                                    }
                                },
                                {
                                    "name": "cluster_overview",
                                    "description": "Get Kafka cluster overview",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {}
                                    }
                                }
                            ]
                        }
                    }
                    """;
            } else if (requestBody.contains("\"method\":\"tools/call\"") || requestBody.contains("\"method\": \"tools/call\"")) {
                return handleToolCall(requestBody);
            } else if (requestBody.contains("\"method\":\"initialize\"") || requestBody.contains("\"method\": \"initialize\"")) {
                return """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {
                                "tools": {}
                            },
                            "serverInfo": {
                                "name": "kafka-mcp-server",
                                "version": "1.0.0"
                            }
                        }
                    }
                    """;
            }

            return """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "error": {
                        "code": -32601,
                        "message": "Method not found"
                    }
                }
                """;
        } catch (Exception e) {
            log.error("Error processing JSON-RPC", e);
            return """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "error": {
                        "code": -32603,
                        "message": "Internal error: %s"
                    }
                }
                """.formatted(e.getMessage());
        }
    }

    private String handleToolCall(String requestBody) {
        try {
            // Extract tool name and arguments (simple extraction)
            String toolName = extractJsonField(requestBody, "name");
            
            return switch (toolName) {
                case "create_topic" -> {
                    String topicName = extractNestedField(requestBody, "arguments", "topic_name");
                    int partitions = extractIntField(requestBody, "partitions", 1);
                    short replication = (short) extractIntField(requestBody, "replication_factor", 1);
                    
                    Map<String, Object> result = kafkaAdmin.createTopic(topicName, partitions, replication);
                    yield String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": 1,
                            "result": {
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "✓ Topic '%s' created successfully with %d partitions"
                                    }
                                ]
                            }
                        }
                        """, topicName, partitions);
                }
                case "list_topics" -> {
                    var topics = kafkaAdmin.listTopics();
                    String topicList = String.join(", ", topics);
                    yield String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": 1,
                            "result": {
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "Topics (%d): %s"
                                    }
                                ]
                            }
                        }
                        """, topics.size(), topicList);
                }
                case "describe_topic" -> {
                    String topicName = extractNestedField(requestBody, "arguments", "topic_name");
                    Map<String, Object> details = kafkaAdmin.describeTopic(topicName);
                    yield String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": 1,
                            "result": {
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "Topic '%s':\\n%s"
                                    }
                                ]
                            }
                        }
                        """, topicName, formatMap(details));
                }
                case "delete_topic" -> {
                    String topicName = extractNestedField(requestBody, "arguments", "topic_name");
                    kafkaAdmin.deleteTopic(topicName);
                    yield String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": 1,
                            "result": {
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "✓ Topic '%s' deleted successfully"
                                    }
                                ]
                            }
                        }
                        """, topicName);
                }
                case "topic_exists" -> {
                    String topicName = extractNestedField(requestBody, "arguments", "topic_name");
                    boolean exists = kafkaAdmin.topicExists(topicName);
                    yield String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": 1,
                            "result": {
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "Topic '%s' %s"
                                    }
                                ]
                            }
                        }
                        """, topicName, exists ? "exists" : "does not exist");
                }
                case "cluster_overview" -> {
                    Map<String, Object> overview = kafkaAdmin.getClusterOverview();
                    yield String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": 1,
                            "result": {
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "Cluster Overview:\\n%s"
                                    }
                                ]
                            }
                        }
                        """, formatMap(overview));
                }
                default -> """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "error": {
                            "code": -32601,
                            "message": "Unknown tool: %s"
                        }
                    }
                    """.formatted(toolName);
            };
        } catch (Exception e) {
            log.error("Error executing tool", e);
            return String.format("""
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "error": {
                        "code": -32603,
                        "message": "Tool execution failed: %s"
                    }
                }
                """, e.getMessage());
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        
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
        
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = """
            {
                "jsonrpc": "2.0",
                "id": null,
                "error": {
                    "code": %d,
                    "message": "%s"
                }
            }
            """.formatted(statusCode, message);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    // Simple JSON field extractors (not production-grade)
    private String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private String extractNestedField(String json, String parentField, String childField) {
        // Find the arguments object
        int argsStart = json.indexOf("\"" + parentField + "\"");
        if (argsStart == -1) return "";
        
        String argsSection = json.substring(argsStart);
        return extractJsonField(argsSection, childField);
    }

    private int extractIntField(String json, String fieldName, int defaultValue) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultValue;
    }

    private String formatMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((key, value) -> sb.append("  ").append(key).append(": ").append(value).append("\\n"));
        return sb.toString();
    }
}
