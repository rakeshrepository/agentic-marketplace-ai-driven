package com.agentic.marketplace.registry.config;

import com.agentic.marketplace.registry.model.Agent;
import com.agentic.marketplace.registry.model.AgentCapability;
import com.agentic.marketplace.registry.model.Category;
import com.agentic.marketplace.registry.repository.AgentRepository;
import com.agentic.marketplace.registry.repository.CategoryRepository;
import com.agentic.marketplace.sdk.loader.AgentRegistryLoader;
import com.agentic.marketplace.sdk.model.AgentMetadata;
import com.agentic.marketplace.sdk.model.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final CategoryRepository categoryRepository;
    private final AgentRepository agentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing agent registry database...");
        
        // Only initialize if categories don't exist
        long categoryCount = categoryRepository.count();
        long agentCount = agentRepository.count();
        
        log.info("Found {} categories and {} agents in database", categoryCount, agentCount);
        
        if (categoryCount >= 9 && agentCount >= 27) {
            log.info("Database already initialized with all data. Skipping seed data.");
            return;
        }
        
        if (categoryCount > 0 || agentCount > 0) {
            log.warn("Database has partial data. Clearing and reinitializing...");
            agentRepository.deleteAll();
            categoryRepository.deleteAll();
        }
        
        initializeCategories();
        initializeAgents();
        
        log.info("Database initialization completed successfully! Added {} categories and 27 agents", categoryRepository.count());
    }

    private void initializeCategories() {
        List<Category> categories = Arrays.asList(
            createCategory("infrastructure", "⚡ Infrastructure",
                "Power your cloud infrastructure - Manage databases, message queues, caching, and storage systems.",
                "server"),
            
            createCategory("devops", "🔧 DevOps",
                "Orchestrate your operations - Container management, Kubernetes, infrastructure as code, and cloud provisioning.",
                "settings"),
            
            createCategory("developer-tools", "🚀 Developer Tools",
                "Accelerate your development workflow - Code generation, documentation, Git workflows, and developer productivity.",
                "code"),
            
            createCategory("cicd-automation", "🔄 CI/CD & Automation",
                "Automate everything, deploy with confidence - Pipeline management, testing automation, and release orchestration.",
                "pipeline"),
            
            createCategory("data-analytics", "📊 Data & Analytics",
                "Transform data into insights - Business analytics, ETL pipelines, data quality, and visualization.",
                "chart"),
            
            createCategory("security-monitoring", "🛡️ Security & Monitoring",
                "Secure, monitor, and protect - Security scanning, log analysis, performance monitoring, and alerts.",
                "shield"),
            
            createCategory("ai-intelligent", "💬 AI & Intelligent Agents",
                "AI-powered automation at your fingertips - Chatbots, customer support, and document processing.",
                "brain"),
            
            createCategory("integration-hub", "🌐 Integration Hub",
                "Connect all your tools seamlessly - Webhooks, API connectors, and multi-channel notifications.",
                "link"),
            
            createCategory("custom-solutions", "🧩 Custom Solutions",
                "Your custom-built marketplace agents - Business-specific workflows and tailored integrations.",
                "puzzle")
        );
        
        categoryRepository.saveAll(categories);
        log.info("Initialized {} categories", categories.size());
    }

    private void initializeAgents() {
        log.info("Loading agents from agent-list.json...");
        
        try {
            AgentRegistry registry = AgentRegistryLoader.loadRegistry();
            
            for (AgentMetadata agentMetadata : registry.getAgents()) {
                log.info("Creating agent: {} with endpoint: {}", agentMetadata.getId(), agentMetadata.getEndpoint());
                
                agentRepository.save(createAgent(
                    agentMetadata.getId(),
                    agentMetadata.getName(),
                    agentMetadata.getDescription(),
                    agentMetadata.getCategory(),
                    agentMetadata.getEndpoint(), // This now comes from agent-list.json - SINGLE SOURCE OF TRUTH
                    agentMetadata.getIcon(),
                    getColorForCategory(agentMetadata.getCategory()),
                    agentMetadata.getStatus(),
                    agentMetadata.getCapabilities()
                ));
            }
            
            log.info("Successfully loaded {} agents from agent-list.json", registry.getAgents().size());
        } catch (Exception e) {
            log.error("Failed to load agents from agent-list.json. Falling back to hardcoded agents.", e);
            initializeAgentsHardcoded();
        }
    }
    
    private String getColorForCategory(String category) {
        return switch (category) {
            case "infrastructure" -> "#FF6B6B";
            case "devops" -> "#326CE5";
            case "developer-tools" -> "#F7DF1E";
            case "cicd-automation" -> "#2088FF";
            case "data-analytics" -> "#9B59B6";
            case "security-monitoring" -> "#E74C3C";
            case "ai-intelligent" -> "#3498DB";
            case "integration-hub" -> "#1ABC9C";
            case "custom-solutions" -> "#95A5A6";
            default -> "#4ECDC4";
        };
    }
    
    private void initializeAgentsHardcoded() {
        log.warn("Using hardcoded agent initialization as fallback");
        
        // Infrastructure Agents - USING NGINX PROXY PATHS
        agentRepository.save(createAgent(
            "topic-management-agent", "Kafka Topic Management Agent",
            "Manage Kafka topics using natural language. Create, list, delete, and describe topics in your Kafka cluster.",
            "infrastructure", "/agents/topic-management-agent",
            "kafka", "#FF6B6B", "active",
            Arrays.asList("create-topic", "list-topics", "delete-topic", "describe-topic")
        ));
        
        agentRepository.save(createAgent(
            "database-agent", "Database Management Agent",
            "Manage database tables using natural language. Create, list, describe, and drop tables in your H2 database.",
            "infrastructure", "/agents/database-management-agent",
            "database", "#4ECDC4", "active",
            Arrays.asList("create-table", "list-tables", "describe-table", "drop-table")
        ));
        
        agentRepository.save(createAgent(
            "redis-cache-agent", "Redis Cache Agent",
            "Manage Redis cache instances, monitor performance, and optimize cache strategies.",
            "infrastructure", "http://redis-agent:8085/api/redis-agent",
            "storage", "#95E1D3", "coming-soon",
            Arrays.asList("cache-management", "key-operations", "performance-monitoring")
        ));

        // DevOps Agents
        agentRepository.save(createAgent(
            "kubernetes-agent", "Kubernetes Orchestration Agent",
            "Deploy, scale, and manage Kubernetes resources with conversational commands.",
            "devops", "http://k8s-agent:8087/api/k8s-agent",
            "cloud", "#326CE5", "coming-soon",
            Arrays.asList("deploy-pods", "scale-deployments", "manage-services")
        ));
        
        agentRepository.save(createAgent(
            "docker-agent", "Docker Container Agent",
            "Build, run, and manage Docker containers and images using natural language commands.",
            "devops", "http://docker-agent:8088/api/docker-agent",
            "container", "#2496ED", "coming-soon",
            Arrays.asList("container-management", "image-building", "docker-compose")
        ));
        
        agentRepository.save(createAgent(
            "terraform-agent", "Infrastructure as Code Agent",
            "Create and manage Terraform configurations, provision cloud resources automatically.",
            "devops", "http://terraform-agent:8089/api/terraform-agent",
            "cloud", "#7B42BC", "coming-soon",
            Arrays.asList("terraform-planning", "resource-provisioning", "state-management")
        ));

        // Developer Tools Agents
        agentRepository.save(createAgent(
            "code-generator-agent", "Code Generation Agent",
            "Generate boilerplate code, refactor existing code, and create unit tests automatically.",
            "developer-tools", "http://code-gen-agent:8091/api/code-gen-agent",
            "code", "#F7DF1E", "coming-soon",
            Arrays.asList("code-generation", "refactoring", "test-creation")
        ));
        
        agentRepository.save(createAgent(
            "api-documentation-agent", "API Documentation Agent",
            "Automatically generate and maintain API documentation from your codebase.",
            "developer-tools", "http://doc-agent:8092/api/doc-agent",
            "book", "#41B883", "coming-soon",
            Arrays.asList("api-docs", "swagger-generation", "changelog-updates")
        ));
        
        agentRepository.save(createAgent(
            "git-workflow-agent", "Git Workflow Agent",
            "Manage Git operations, create branches, handle merge conflicts, and automate Git workflows.",
            "developer-tools", "http://git-agent:8093/api/git-agent",
            "git", "#F05032", "coming-soon",
            Arrays.asList("branch-management", "merge-operations", "commit-analysis")
        ));

        // CI/CD & Automation Agents
        agentRepository.save(createAgent(
            "ci-cd-agent", "CI/CD Pipeline Agent",
            "Manage CI/CD pipelines, trigger builds, and monitor deployment status across multiple platforms.",
            "cicd-automation", "http://cicd-agent:8094/api/cicd-agent",
            "pipeline", "#2088FF", "coming-soon",
            Arrays.asList("pipeline-management", "build-triggers", "deployment-monitoring")
        ));
        
        agentRepository.save(createAgent(
            "testing-agent", "Automated Testing Agent",
            "Create, run, and analyze automated tests including unit, integration, and E2E tests.",
            "cicd-automation", "http://testing-agent:8095/api/testing-agent",
            "test", "#17A2B8", "coming-soon",
            Arrays.asList("test-generation", "test-execution", "coverage-analysis")
        ));
        
        agentRepository.save(createAgent(
            "release-agent", "Release Automation Agent",
            "Automate software releases, manage versioning, and generate release notes automatically.",
            "cicd-automation", "http://release-agent:8096/api/release-agent",
            "release", "#28A745", "coming-soon",
            Arrays.asList("version-management", "release-notes", "deployment-automation")
        ));

        // Data & Analytics Agents
        agentRepository.save(createAgent(
            "business-analytics-agent", "Business Analytics Agent",
            "Generate reports, analyze business metrics, and provide data-driven insights with AI-powered analytics.",
            "data-analytics", "http://analytics-agent:8097/api/analytics-agent",
            "chart", "#FF6384", "coming-soon",
            Arrays.asList("report-generation", "metric-analysis", "data-visualization")
        ));
        
        agentRepository.save(createAgent(
            "etl-pipeline-agent", "ETL Pipeline Agent",
            "Create and manage data extraction, transformation, and loading pipelines effortlessly.",
            "data-analytics", "http://etl-agent:8098/api/etl-agent",
            "pipeline", "#36A2EB", "coming-soon",
            Arrays.asList("data-extraction", "transformation", "data-loading")
        ));
        
        agentRepository.save(createAgent(
            "data-quality-agent", "Data Quality Agent",
            "Monitor data quality, detect anomalies, and ensure data consistency across systems.",
            "data-analytics", "http://data-quality-agent:8099/api/data-quality-agent",
            "quality", "#FFCE56", "coming-soon",
            Arrays.asList("quality-checks", "anomaly-detection", "data-validation")
        ));

        // Security & Monitoring Agents
        agentRepository.save(createAgent(
            "security-scan-agent", "Security Scanning Agent",
            "Scan code for vulnerabilities, detect security issues, and suggest fixes automatically.",
            "security-monitoring", "http://security-agent:8100/api/security-agent",
            "shield", "#DC3545", "coming-soon",
            Arrays.asList("vulnerability-scanning", "security-analysis", "compliance-checks")
        ));
        
        agentRepository.save(createAgent(
            "log-analysis-agent", "Log Analysis Agent",
            "Analyze application logs, detect errors, and provide intelligent insights from log data.",
            "security-monitoring", "http://log-agent:8101/api/log-agent",
            "logs", "#6C757D", "coming-soon",
            Arrays.asList("log-parsing", "error-detection", "pattern-analysis")
        ));
        
        agentRepository.save(createAgent(
            "performance-monitoring-agent", "Performance Monitoring Agent",
            "Monitor application performance, track metrics, and get alerts on performance issues.",
            "security-monitoring", "http://performance-agent:8102/api/performance-agent",
            "performance", "#FFC107", "coming-soon",
            Arrays.asList("metrics-tracking", "alert-management", "performance-optimization")
        ));

        // AI & Intelligent Agents
        agentRepository.save(createAgent(
            "customer-support-agent", "Customer Support Agent",
            "Handle customer inquiries, ticket management, and provide automated support responses with AI.",
            "ai-intelligent", "http://support-agent:8103/api/support-agent",
            "support", "#20C997", "coming-soon",
            Arrays.asList("ticket-management", "customer-queries", "automated-responses")
        ));
        
        agentRepository.save(createAgent(
            "chatbot-agent", "Smart Chatbot Agent",
            "Create and deploy intelligent chatbots for websites, Slack, Teams, and other platforms.",
            "ai-intelligent", "http://chatbot-agent:8104/api/chatbot-agent",
            "chat", "#6F42C1", "coming-soon",
            Arrays.asList("conversation-management", "multi-platform", "intent-recognition")
        ));
        
        agentRepository.save(createAgent(
            "document-processing-agent", "Document Processing Agent",
            "Extract information from documents, PDFs, invoices using AI-powered OCR and NLP.",
            "ai-intelligent", "http://doc-processing-agent:8105/api/doc-processing-agent",
            "document", "#E83E8C", "coming-soon",
            Arrays.asList("ocr-processing", "data-extraction", "document-classification")
        ));

        // Integration Hub Agents
        agentRepository.save(createAgent(
            "webhook-manager-agent", "Webhook Manager Agent",
            "Create, manage, and monitor webhooks across multiple services and platforms.",
            "integration-hub", "http://webhook-agent:8106/api/webhook-agent",
            "webhook", "#17A2B8", "coming-soon",
            Arrays.asList("webhook-creation", "event-monitoring", "payload-transformation")
        ));
        
        agentRepository.save(createAgent(
            "api-connector-agent", "API Connector Agent",
            "Connect to third-party APIs, manage authentication, and orchestrate API calls seamlessly.",
            "integration-hub", "http://connector-agent:8107/api/connector-agent",
            "api", "#007BFF", "coming-soon",
            Arrays.asList("api-integration", "auth-management", "request-orchestration")
        ));
        
        agentRepository.save(createAgent(
            "notification-agent", "Notification Hub Agent",
            "Send notifications via email, SMS, Slack, Teams, and other channels from a unified interface.",
            "integration-hub", "http://notification-agent:8108/api/notification-agent",
            "notification", "#FD7E14", "coming-soon",
            Arrays.asList("multi-channel", "template-management", "delivery-tracking")
        ));

        // Custom Solutions Agents
        agentRepository.save(createAgent(
            "custom-workflow-agent", "Custom Workflow Agent",
            "Build and execute custom business workflows tailored to your organization's needs.",
            "custom-solutions", "http://custom-workflow-agent:8109/api/custom-workflow-agent",
            "workflow", "#6610F2", "coming-soon",
            Arrays.asList("workflow-builder", "task-automation", "business-logic")
        ));
        
        agentRepository.save(createAgent(
            "custom-integration-agent", "Custom Integration Agent",
            "Create custom integrations for your specific business systems and internal tools.",
            "custom-solutions", "http://custom-integration-agent:8110/api/custom-integration-agent",
            "puzzle", "#6C757D", "coming-soon",
            Arrays.asList("custom-connectors", "data-mapping", "integration-logic")
        ));
        
        log.info("Initialized 27 agents across 9 categories");
    }

    private Category createCategory(String id, String name, String description, String icon) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDescription(description);
        category.setIcon(icon);
        return category;
    }

    private Agent createAgent(String id, String name, String description, String categoryId,
                             String endpointUrl, String icon, String color, String status,
                             List<String> capabilityIds) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setDescription(description);
        agent.setCategoryId(categoryId);
        agent.setEndpointUrl(endpointUrl);
        agent.setHealthCheckPath("/actuator/health");
        agent.setIcon(icon);
        agent.setColor(color);
        agent.setStatus(status);
        
        // Add capabilities
        for (String capId : capabilityIds) {
            AgentCapability capability = new AgentCapability();
            capability.setCapabilityId(capId);
            capability.setCapabilityName(formatCapabilityName(capId));
            capability.setDescription("Capability for " + formatCapabilityName(capId));
            capability.setAgent(agent);
            agent.getCapabilities().add(capability);
        }
        
        return agent;
    }

    private String formatCapabilityName(String capabilityId) {
        return Arrays.stream(capabilityId.split("-"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
            .reduce((a, b) -> a + " " + b)
            .orElse(capabilityId);
    }
}
