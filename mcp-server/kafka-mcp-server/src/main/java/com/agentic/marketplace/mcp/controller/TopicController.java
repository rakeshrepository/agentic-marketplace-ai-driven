package com.agentic.marketplace.mcp.controller;

import com.agentic.marketplace.mcp.model.CreateTopicRequest;
import com.agentic.marketplace.mcp.model.TopicDetails;
import com.agentic.marketplace.mcp.service.KafkaAdminService;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopicController {

    private final KafkaAdminService kafkaAdminService;

    @PostMapping
    public ResponseEntity<AgentResponse> createTopic(@RequestBody CreateTopicRequest request) {
        log.info("Create topic request: {}", request);
        try {
            if (request.getTopicName() == null || request.getTopicName().isBlank()) {
                return buildBadRequestResponse("Topic name is required");
            }
            
            if (kafkaAdminService.topicExists(request.getTopicName())) {
                return buildBadRequestResponse("Topic already exists: " + request.getTopicName());
            }
            
            CreateTopicRequest createdTopic = kafkaAdminService.createTopic(request);
            return buildSuccessResponse("Topic created successfully", createdTopic);
        } catch (Exception e) {
            log.error("Error creating topic", e);
            return buildErrorResponse("Failed to create topic: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<AgentResponse> listTopics() {
        log.info("List topics request");
        try {
            List<String> topics = kafkaAdminService.listTopics();
            return buildSuccessResponse("Found " + topics.size() + " topics", topics);
        } catch (Exception e) {
            log.error("Error listing topics", e);
            return buildErrorResponse("Failed to list topics: " + e.getMessage());
        }
    }

    @GetMapping("/{topicName}")
    public ResponseEntity<AgentResponse> describeTopic(@PathVariable("topicName") String topicName) {
        log.info("Describe topic request: {}", topicName);
        try {
            if (!kafkaAdminService.topicExists(topicName)) {
                return ResponseEntity.notFound().build();
            }
            
            TopicDetails details = kafkaAdminService.describeTopic(topicName);
            return buildSuccessResponse("Topic details retrieved", details);
        } catch (Exception e) {
            log.error("Error describing topic", e);
            return buildErrorResponse("Failed to describe topic: " + e.getMessage());
        }
    }

    @DeleteMapping("/{topicName}")
    public ResponseEntity<AgentResponse> deleteTopic(@PathVariable("topicName") String topicName) {
        log.info("Delete topic request: {}", topicName);
        try {
            if (!kafkaAdminService.topicExists(topicName)) {
                return ResponseEntity.notFound().build();
            }
            
            kafkaAdminService.deleteTopic(topicName);
            return buildSuccessResponse("Topic deleted successfully", null);
        } catch (Exception e) {
            log.error("Error deleting topic", e);
            return buildErrorResponse("Failed to delete topic: " + e.getMessage());
        }
    }

    /**
     * Helper method to build success response with data.
     */
    private ResponseEntity<AgentResponse> buildSuccessResponse(String message, Object data) {
        return ResponseEntity.ok(AgentResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build());
    }

    /**
     * Helper method to build bad request error response.
     */
    private ResponseEntity<AgentResponse> buildBadRequestResponse(String error) {
        return ResponseEntity.badRequest().body(AgentResponse.builder()
                .success(false)
                .error(error)
                .build());
    }

    /**
     * Helper method to build internal server error response.
     */
    private ResponseEntity<AgentResponse> buildErrorResponse(String error) {
        return ResponseEntity.internalServerError().body(AgentResponse.builder()
                .success(false)
                .error(error)
                .build());
    }
}
