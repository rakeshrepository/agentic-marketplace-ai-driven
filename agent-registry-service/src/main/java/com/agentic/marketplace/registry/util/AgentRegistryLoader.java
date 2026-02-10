package com.agentic.marketplace.registry.util;

import com.agentic.marketplace.registry.model.AgentRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public class AgentRegistryLoader {

    private static final String AGENT_LIST_PATH = "agent-list.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static AgentRegistry loadRegistry() {
        try (InputStream is = AgentRegistryLoader.class.getClassLoader().getResourceAsStream(AGENT_LIST_PATH)) {
            if (is == null) {
                throw new RuntimeException("Could not find " + AGENT_LIST_PATH + " in classpath");
            }
            return objectMapper.readValue(is, AgentRegistry.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load agent registry", e);
        }
    }

    public static AgentRegistry loadRegistry(String path) {
        try (InputStream is = AgentRegistryLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Could not find " + path + " in classpath");
            }
            return objectMapper.readValue(is, AgentRegistry.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load agent registry from " + path, e);
        }
    }
}
