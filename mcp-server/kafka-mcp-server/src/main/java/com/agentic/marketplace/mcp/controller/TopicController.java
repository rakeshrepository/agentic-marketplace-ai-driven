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
                return ResponseEntity.badRequest().body(AgentResponse.builder()
                        .success(false)
                        .error("Topic name is required")
                        .build());
            }
            
            if (kafkaAdminService.topicExists(request.getTopicName())) {
                return ResponseEntity.badRequest().body(AgentResponse.builder()
                        .success(false)
                        .error("Topic already exists: " + request.getTopicName())
                        .build());
            }
            
            CreateTopicRequest createdTopic = kafkaAdminService.createTopic(request);
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Topic created successfully")
                    .data(createdTopic)
                    .build());
        } catch (Exception e) {
            log.error("Error creating topic", e);
            return ResponseEntity.internalServerError().body(AgentResponse.builder()
                    .success(false)
                    .error("Failed to create topic: " + e.getMessage())
                    .build());
        }
    }

    @GetMapping
    public ResponseEntity<AgentResponse> listTopics() {
        log.info("List topics request");
        try {
            List<String> topics = kafkaAdminService.listTopics();
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Found " + topics.size() + " topics")
                    .data(topics)
                    .build());
        } catch (Exception e) {
            log.error("Error listing topics", e);
            return ResponseEntity.internalServerError().body(AgentResponse.builder()
                    .success(false)
                    .error("Failed to list topics: " + e.getMessage())
                    .build());
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
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Topic details retrieved")
                    .data(details)
                    .build());
        } catch (Exception e) {
            log.error("Error describing topic", e);
            return ResponseEntity.internalServerError().body(AgentResponse.builder()
                    .success(false)
                    .error("Failed to describe topic: " + e.getMessage())
                    .build());
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
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Topic deleted successfully")
                    .build());
        } catch (Exception e) {
            log.error("Error deleting topic", e);
            return ResponseEntity.internalServerError().body(AgentResponse.builder()
                    .success(false)
                    .error("Failed to delete topic: " + e.getMessage())
                    .build());
        }
    }
}
