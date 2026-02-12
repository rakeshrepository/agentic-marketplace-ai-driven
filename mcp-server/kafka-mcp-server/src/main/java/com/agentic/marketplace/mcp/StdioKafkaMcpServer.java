package com.agentic.marketplace.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * STDIO-based MCP Server for VS Code Integration
 * 
 * Communicates via standard input/output using line-delimited JSON-RPC 2.0
 * This allows VS Code extensions (Claude Code, Cline, etc.) to spawn this server
 * as a subprocess and communicate through pipes.
 * 
 * Protocol: JSON-RPC 2.0
 * Transport: STDIO (stdin/stdout)
 * Format: Each message is a single line of JSON followed by newline
 */
public class StdioKafkaMcpServer {
    private static final Logger log = LoggerFactory.getLogger(StdioKafkaMcpServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaAdminService kafkaAdmin;
    private boolean running = true;

    public StdioKafkaMcpServer(KafkaAdminService kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    public static void main(String[] args) {
        // Redirect logging to stderr to keep stdout clean for JSON-RPC
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
        
        log.info("=== Starting Kafka MCP Server (STDIO Mode for VS Code) ===");
        
        // Load configuration from environment
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        int requestTimeout = getEnvAsInt("KAFKA_ADMIN_TIMEOUT", 10000);
        
        KafkaAdminService kafkaAdmin = new KafkaAdminService(bootstrapServers, requestTimeout);
        StdioKafkaMcpServer server = new StdioKafkaMcpServer(kafkaAdmin);
        
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down MCP server...");
            server.stop();
            kafkaAdmin.close();
        }));
        
        // Start processing STDIO
        server.start();
    }

    public void start() {
        log.info("✓ MCP STDIO Server started - waiting for messages on stdin");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            
            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    log.debug("Received: {}", line);
                    
                    // Parse JSON-RPC request
                    JsonNode request = objectMapper.readTree(line);
                    
                    // Process request
                    String response = processRequest(request);
                    
                    // Write response (single line + newline)
                    writer.write(response);
                    writer.newLine();
                    writer.flush();
                    
                    log.debug("Sent: {}", response);
                    
                } catch (Exception e) {
                    log.error("Error processing request", e);
                    // Send error response
                    String errorResponse = createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
                    writer.write(errorResponse);
                    writer.newLine();
                    writer.flush();
                }
            }
            
        } catch (Exception e) {
            log.error("Fatal error in STDIO server", e);
        }
        
        log.info("MCP STDIO Server stopped");
    }

    public void stop() {
        running = false;
    }

    private String processRequest(JsonNode request) throws Exception {
        String method = request.path("method").asText();
        JsonNode idNode = request.get("id");
        JsonNode params = request.path("params");
        
        log.info("Processing method: {}", method);
        
        return switch (method) {
            case "initialize" -> handleInitialize(idNode, params);
            case "tools/list" -> handleToolsList(idNode);
            case "tools/call" -> handleToolCall(idNode, params);
            case "ping" -> handlePing(idNode);
            default -> createErrorResponse(idNode, -32601, "Method not found: " + method);
        };
    }

    private String handleInitialize(JsonNode id, JsonNode params) throws Exception {
        log.info("Client initialized: {}", params.path("clientInfo"));
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "kafka-mcp-server");
        serverInfo.put("version", "1.0.0");
        
        return createSuccessResponse(id, result);
    }

    private String handleToolsList(JsonNode id) throws Exception {
        ObjectNode result = objectMapper.createObjectNode();
        
        // Add all Kafka tools
        var tools = result.putArray("tools");
        
        // Tool 1: create_topic
        ObjectNode createTopic = objectMapper.createObjectNode();
        createTopic.put("name", "create_topic");
        createTopic.put("description", "Creates a new Kafka topic");
        ObjectNode createSchema = createTopic.putObject("inputSchema");
        createSchema.put("type", "object");
        ObjectNode createProps = createSchema.putObject("properties");
        createProps.putObject("topic_name")
            .put("type", "string")
            .put("description", "Name of the Kafka topic to create");
        createProps.putObject("partitions")
            .put("type", "integer")
            .put("description", "Number of partitions (default: 1)");
        createProps.putObject("replication_factor")
            .put("type", "integer")
            .put("description", "Replication factor (default: 1)");
        createSchema.putArray("required").add("topic_name");
        tools.add(createTopic);
        
        // Tool 2: list_topics
        ObjectNode listTopics = objectMapper.createObjectNode();
        listTopics.put("name", "list_topics");
        listTopics.put("description", "Lists all Kafka topics");
        ObjectNode listSchema = listTopics.putObject("inputSchema");
        listSchema.put("type", "object");
        listSchema.putObject("properties");
        tools.add(listTopics);
        
        // Tool 3: describe_topic
        ObjectNode describeTopic = objectMapper.createObjectNode();
        describeTopic.put("name", "describe_topic");
        describeTopic.put("description", "Get detailed information about a specific Kafka topic");
        ObjectNode describeSchema = describeTopic.putObject("inputSchema");
        describeSchema.put("type", "object");
        ObjectNode describeProps = describeSchema.putObject("properties");
        describeProps.putObject("topic_name")
            .put("type", "string")
            .put("description", "Name of the topic to describe");
        describeSchema.putArray("required").add("topic_name");
        tools.add(describeTopic);
        
        // Tool 4: update_topic
        ObjectNode updateTopic = objectMapper.createObjectNode();
        updateTopic.put("name", "update_topic");
        updateTopic.put("description", "Update the number of partitions for a Kafka topic");
        ObjectNode updateSchema = updateTopic.putObject("inputSchema");
        updateSchema.put("type", "object");
        ObjectNode updateProps = updateSchema.putObject("properties");
        updateProps.putObject("topic_name")
            .put("type", "string")
            .put("description", "Name of the topic to update");
        updateProps.putObject("partitions")
            .put("type", "integer")
            .put("description", "New number of partitions (must be greater than current)");
        updateSchema.putArray("required").add("topic_name").add("partitions");
        tools.add(updateTopic);
        
        // Tool 5: delete_topic
        ObjectNode deleteTopic = objectMapper.createObjectNode();
        deleteTopic.put("name", "delete_topic");
        deleteTopic.put("description", "Delete a Kafka topic (WARNING: This operation is irreversible!)");
        ObjectNode deleteSchema = deleteTopic.putObject("inputSchema");
        deleteSchema.put("type", "object");
        ObjectNode deleteProps = deleteSchema.putObject("properties");
        deleteProps.putObject("topic_name")
            .put("type", "string")
            .put("description", "Name of the topic to delete");
        deleteSchema.putArray("required").add("topic_name");
        tools.add(deleteTopic);
        
        // Tool 6: topic_exists
        ObjectNode topicExists = objectMapper.createObjectNode();
        topicExists.put("name", "topic_exists");
        topicExists.put("description", "Check if a Kafka topic exists");
        ObjectNode existsSchema = topicExists.putObject("inputSchema");
        existsSchema.put("type", "object");
        ObjectNode existsProps = existsSchema.putObject("properties");
        existsProps.putObject("topic_name")
            .put("type", "string")
            .put("description", "Name of the topic to check");
        existsSchema.putArray("required").add("topic_name");
        tools.add(topicExists);
        
        // Tool 7: cluster_overview
        ObjectNode clusterOverview = objectMapper.createObjectNode();
        clusterOverview.put("name", "cluster_overview");
        clusterOverview.put("description", "Get an overview of the Kafka cluster");
        ObjectNode overviewSchema = clusterOverview.putObject("inputSchema");
        overviewSchema.put("type", "object");
        overviewSchema.putObject("properties");
        tools.add(clusterOverview);
        
        return createSuccessResponse(id, result);
    }

    private String handleToolCall(JsonNode id, JsonNode params) throws Exception {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        
        log.info("Calling tool: {} with args: {}", toolName, arguments);
        
        String resultText = switch (toolName) {
            case "create_topic" -> {
                String topicName = arguments.path("topic_name").asText();
                int partitions = arguments.path("partitions").asInt(1);
                short replication = (short) arguments.path("replication_factor").asInt(1);
                
                Map<String, Object> result = kafkaAdmin.createTopic(topicName, partitions, replication);
                yield "✓ Topic created successfully:\n" + formatMap(result);
            }
            case "list_topics" -> {
                var topics = kafkaAdmin.listTopics();
                yield "📋 Available Kafka topics (" + topics.size() + "):\n" +
                      String.join("\n", topics.stream().map(t -> "  • " + t).toList());
            }
            case "describe_topic" -> {
                String topicName = arguments.path("topic_name").asText();
                Map<String, Object> result = kafkaAdmin.describeTopic(topicName);
                yield "📊 Topic details for '" + topicName + "':\n" + formatMap(result);
            }
            case "update_topic" -> {
                String topicName = arguments.path("topic_name").asText();
                int partitions = arguments.path("partitions").asInt();
                Map<String, Object> result = kafkaAdmin.updateTopic(topicName, partitions, null);
                yield "✓ Topic updated successfully:\n" + formatMap(result);
            }
            case "delete_topic" -> {
                String topicName = arguments.path("topic_name").asText();
                Map<String, Object> result = kafkaAdmin.deleteTopic(topicName);
                yield "✓ Topic deleted successfully:\n" + formatMap(result);
            }
            case "topic_exists" -> {
                String topicName = arguments.path("topic_name").asText();
                boolean exists = kafkaAdmin.topicExists(topicName);
                yield exists ? 
                    "✓ Topic '" + topicName + "' exists" : 
                    "✗ Topic '" + topicName + "' does not exist";
            }
            case "cluster_overview" -> {
                Map<String, Object> result = kafkaAdmin.getClusterOverview();
                yield "🖥️  Kafka Cluster Overview:\n" + formatMap(result);
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
        
        // Create MCP tool result format
        ObjectNode result = objectMapper.createObjectNode();
        var content = result.putArray("content");
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", resultText);
        content.add(textContent);
        
        return createSuccessResponse(id, result);
    }

    private String handlePing(JsonNode id) throws Exception {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        return createSuccessResponse(id, result);
    }

    private String createSuccessResponse(JsonNode id, ObjectNode result) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        response.set("result", result);
        return objectMapper.writeValueAsString(response);
    }

    private String createErrorResponse(JsonNode id, int code, String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) {
                response.set("id", id);
            }
            ObjectNode error = response.putObject("error");
            error.put("code", code);
            error.put("message", message);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to create error response", e);
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    private String formatMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((key, value) -> 
            sb.append("  ").append(key).append(": ").append(value).append("\n")
        );
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
