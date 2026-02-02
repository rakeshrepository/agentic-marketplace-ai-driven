package com.agentic.marketplace.mcp.service;

import com.agentic.marketplace.mcp.config.KafkaConfig;
import com.agentic.marketplace.mcp.model.CreateTopicRequest;
import com.agentic.marketplace.mcp.model.TopicDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

    private final AdminClient adminClient;
    private final KafkaConfig kafkaConfig;

    public CreateTopicRequest createTopic(CreateTopicRequest request) throws ExecutionException, InterruptedException {
        int partitions = request.getPartitions() != null 
                ? request.getPartitions() 
                : kafkaConfig.getAdmin().getDefaultPartitions();
        
        short replicationFactor = request.getReplicationFactor() != null 
                ? request.getReplicationFactor().shortValue() 
                : (short) kafkaConfig.getAdmin().getDefaultReplicationFactor();

        NewTopic newTopic = new NewTopic(request.getTopicName(), partitions, replicationFactor);
        
        log.info("Creating topic: {} with {} partitions and replication factor {}", 
                request.getTopicName(), partitions, replicationFactor);
        
        CreateTopicsResult result = adminClient.createTopics(Collections.singleton(newTopic));
        result.all().get();
        
        log.info("Successfully created topic: {}", request.getTopicName());
        
        // Return the request with actual values used (including defaults)
        return CreateTopicRequest.builder()
                .topicName(request.getTopicName())
                .partitions(partitions)
                .replicationFactor((int) replicationFactor)
                .build();
    }

    public List<String> listTopics() throws ExecutionException, InterruptedException {
        log.info("Listing all topics");
        ListTopicsResult result = adminClient.listTopics();
        Set<String> topics = result.names().get();
        log.info("Found {} topics", topics.size());
        return new ArrayList<>(topics);
    }

    public void deleteTopic(String topicName) throws ExecutionException, InterruptedException {
        log.info("Deleting topic: {}", topicName);
        DeleteTopicsResult result = adminClient.deleteTopics(Collections.singleton(topicName));
        result.all().get();
        log.info("Successfully deleted topic: {}", topicName);
    }

    public TopicDetails describeTopic(String topicName) throws ExecutionException, InterruptedException {
        log.info("Describing topic: {}", topicName);
        
        // Get topic description
        DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singleton(topicName));
        TopicDescription description = topicsResult.topicNameValues().get(topicName).get();
        
        // Get topic configs
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
        DescribeConfigsResult configsResult = adminClient.describeConfigs(Collections.singleton(resource));
        Config config = configsResult.all().get().get(resource);
        
        Map<String, String> configMap = config.entries().stream()
                .filter(entry -> !entry.isDefault())
                .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value));

        List<TopicDetails.PartitionInfo> partitionInfos = description.partitions().stream()
                .map(p -> TopicDetails.PartitionInfo.builder()
                        .partition(p.partition())
                        .leader(p.leader() != null ? p.leader().id() : -1)
                        .replicas(p.replicas().stream().map(n -> n.id()).collect(Collectors.toList()))
                        .isr(p.isr().stream().map(n -> n.id()).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        int replicationFactor = description.partitions().isEmpty() 
                ? 0 
                : description.partitions().get(0).replicas().size();

        return TopicDetails.builder()
                .name(topicName)
                .partitions(description.partitions().size())
                .replicationFactor(replicationFactor)
                .configs(configMap)
                .partitionInfos(partitionInfos)
                .build();
    }

    public boolean topicExists(String topicName) {
        try {
            ListTopicsResult result = adminClient.listTopics();
            return result.names().get().contains(topicName);
        } catch (Exception e) {
            log.error("Error checking topic existence", e);
            return false;
        }
    }
}
