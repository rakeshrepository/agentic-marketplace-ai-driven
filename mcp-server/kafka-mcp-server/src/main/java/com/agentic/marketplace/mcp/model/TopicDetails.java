package com.agentic.marketplace.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicDetails {
    private String name;
    private int partitions;
    private int replicationFactor;
    private Map<String, String> configs;
    private List<PartitionInfo> partitionInfos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartitionInfo {
        private int partition;
        private int leader;
        private List<Integer> replicas;
        private List<Integer> isr;
    }
}
