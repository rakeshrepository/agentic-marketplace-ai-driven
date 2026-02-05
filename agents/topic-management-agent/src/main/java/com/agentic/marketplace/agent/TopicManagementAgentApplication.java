package com.agentic.marketplace.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Enable scheduled tasks for conversation cleanup
@ComponentScan(basePackages = {
    "com.agentic.marketplace.agent",
    "com.agentic.marketplace.sdk"  // Scan SDK for ConversationService
})
public class TopicManagementAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopicManagementAgentApplication.class, args);
    }
}
