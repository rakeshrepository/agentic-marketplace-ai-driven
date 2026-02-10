package com.agentic.marketplace.mcp;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Standalone Kafka Admin Service (No Spring)
 */
@Slf4j
public class KafkaAdminService {

    private final AdminClient adminClient;

    public KafkaAdminService(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        
        this.adminClient = AdminClient.create(props);
        log.info("Kafka AdminClient initialized: {}", bootstrapServers);
    }

    public Map<String, Object> createTopic(String topicName, int partitions, short replicationFactor) 
            throws ExecutionException, InterruptedException {
        
        NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
        adminClient.createTopics(Collections.singleton(newTopic)).all().get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("topic", topicName);
        response.put("partitions", partitions);
        response.put("replicationFactor", replicationFactor);
        response.put("created", true);
        
        return response;
    }

    public List<String> listTopics() throws ExecutionException, InterruptedException {
        return new ArrayList<>(adminClient.listTopics().names().get());
    }

    public Map<String, Object> describeTopic(String topicName) 
            throws ExecutionException, InterruptedException {
        
        TopicDescription desc = adminClient.describeTopics(Collections.singleton(topicName))
            .allTopicNames().get().get(topicName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("name", desc.name());
        response.put("partitions", desc.partitions().size());
        response.put("isInternal", desc.isInternal());
        
        return response;
    }

    public Map<String, Object> deleteTopic(String topicName) 
            throws ExecutionException, InterruptedException {
        
        adminClient.deleteTopics(Collections.singleton(topicName)).all().get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("topic", topicName);
        response.put("deleted", true);
        
        return response;
    }

    public boolean topicExists(String topicName) throws ExecutionException, InterruptedException {
        return adminClient.listTopics().names().get().contains(topicName);
    }

    public Map<String, Object> getClusterOverview() throws ExecutionException, InterruptedException {
        DescribeClusterResult clusterResult = adminClient.describeCluster();
        
        Map<String, Object> overview = new HashMap<>();
        overview.put("clusterId", clusterResult.clusterId().get());
        overview.put("controller", clusterResult.controller().get().id());
        overview.put("nodes", clusterResult.nodes().get().size());
        overview.put("topicCount", adminClient.listTopics().names().get().size());
        
        return overview;
    }

    public void close() {
        adminClient.close();
    }
}
