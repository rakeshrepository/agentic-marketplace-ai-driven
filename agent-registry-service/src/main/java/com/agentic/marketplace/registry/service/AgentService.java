package com.agentic.marketplace.registry.service;

import com.agentic.marketplace.registry.model.Agent;
import com.agentic.marketplace.registry.model.AgentCapability;
import com.agentic.marketplace.registry.model.AgentExampleQuery;
import com.agentic.marketplace.registry.model.AgentRegistrationRequest;
import com.agentic.marketplace.registry.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    public List<Agent> getActiveAgents() {
        return agentRepository.findByStatus("active");
    }
    
    public Optional<Agent> getAgentById(String id) {
        return agentRepository.findById(id);
    }
    
    public List<Agent> getAgentsByCategory(String categoryId) {
        return agentRepository.findByCategoryId(categoryId);
    }
    
    @Transactional
    public Agent registerAgent(AgentRegistrationRequest request) {
        // Generate unique agent ID
        String agentId = generateAgentId(request.getName());
        
        // Check if agent already exists
        if (agentRepository.findById(agentId).isPresent()) {
            throw new IllegalArgumentException("Agent with similar name already exists");
        }
        
        // Validate endpoint URL
        if (!isValidUrl(request.getEndpointUrl())) {
            throw new IllegalArgumentException("Invalid endpoint URL");
        }
        
        // Transform Docker internal URL to nginx proxy path
        String endpointUrl = transformToNginxPath(request.getEndpointUrl(), agentId);
        
        // Create agent entity
        Agent agent = new Agent();
        agent.setId(agentId);
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setCategoryId(request.getCategoryId());
        agent.setEndpointUrl(endpointUrl);
        agent.setHealthCheckPath("/actuator/health");
        agent.setIcon(request.getIcon() != null ? request.getIcon() : "🤖");
        agent.setColor(request.getColor() != null ? request.getColor() : "#6366F1");
        agent.setStatus("pending"); // New agents start as pending for approval
        
        // Add capabilities
        if (request.getCapabilities() != null) {
            for (AgentRegistrationRequest.CapabilityRequest capReq : request.getCapabilities()) {
                AgentCapability capability = new AgentCapability();
                capability.setCapabilityId(capReq.getCapabilityId());
                capability.setCapabilityName(capReq.getCapabilityName());
                capability.setDescription(capReq.getDescription());
                capability.setAgent(agent);
                agent.getCapabilities().add(capability);
            }
        }
        
        // Add example queries
        if (request.getExampleQueries() != null) {
            int order = 1;
            for (String query : request.getExampleQueries()) {
                AgentExampleQuery example = new AgentExampleQuery();
                example.setQuery(query);
                example.setDisplayOrder(order++);
                example.setAgent(agent);
                agent.getExampleQueries().add(example);
            }
        }
        
        return agentRepository.save(agent);
    }
    
    private String transformToNginxPath(String dockerUrl, String agentId) {
        // Convert Docker internal URL (http://agent-name:8080) to nginx proxy path (/agents/agent-name)
        // Extract the agent name from the Docker URL
        if (dockerUrl.startsWith("http://") && dockerUrl.contains(":")) {
            String agentName = dockerUrl.substring(7, dockerUrl.indexOf(":", 7));
            // Use the actual Docker service name from the URL (not the generated ID)
            return "/agents/" + agentName;
        }
        return dockerUrl; // Return as-is if not a Docker internal URL
    }
    
    private String generateAgentId(String name) {
        // Convert name to kebab-case and append short UUID
        String baseId = name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return baseId + "-" + shortUuid;
    }
    
    @Transactional
    public boolean deleteAgent(String agentId) {
        Optional<Agent> agentOpt = agentRepository.findById(agentId);
        if (agentOpt.isEmpty()) {
            return false;
        }
        
        Agent agent = agentOpt.get();
        // Allow deletion of active and pending agents (hosted agents and self-registered)
        // Don't allow deletion of coming-soon agents
        if ("coming-soon".equals(agent.getStatus())) {
            throw new IllegalArgumentException("Coming-soon agents cannot be deleted");
        }
        
        agentRepository.deleteById(agentId);
        return true;
    }
    
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
