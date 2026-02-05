package com.agentic.marketplace.agent.validator;

import com.agentic.marketplace.agent.constants.KafkaTopicConstants;
import com.agentic.marketplace.agent.model.ParsedIntent;
import org.springframework.stereotype.Component;

/**
 * Validates parsed intents based on business rules.
 * Separates validation logic from orchestration (SRP - Single Responsibility Principle).
 */
@Component
public class IntentValidator {

    /**
     * Validates the parsed intent based on business rules.
     * 
     * @param intent The parsed intent to validate
     * @return Validation error code if invalid, null if valid
     */
    public String validate(ParsedIntent intent) {
        if (intent.getAction() == null || intent.getAction().isBlank()) {
            return null; // Handled separately in orchestrator
        }

        String action = intent.getAction().toLowerCase();

        switch (action) {
            case KafkaTopicConstants.Actions.CREATE:
                return validateCreate(intent);

            case KafkaTopicConstants.Actions.DELETE:
            case KafkaTopicConstants.Actions.DESCRIBE:
                return validateTopicNameRequired(intent, action);

            case KafkaTopicConstants.Actions.LIST:
                return null; // LIST doesn't require any parameters

            default:
                return KafkaTopicConstants.ValidationErrors.UNKNOWN_ACTION + ":" + action;
        }
    }

    /**
     * Validates CREATE action requirements.
     */
    private String validateCreate(ParsedIntent intent) {
        // CREATE requires topic name
        if (intent.getTopicName() == null || intent.getTopicName().trim().isEmpty()) {
            return KafkaTopicConstants.ValidationErrors.MISSING_TOPIC_NAME_FOR_CREATE;
        }

        // Validate partition range
        if (intent.getPartitions() != null) {
            if (intent.getPartitions() < KafkaTopicConstants.ValidationLimits.MIN_PARTITIONS ||
                intent.getPartitions() > KafkaTopicConstants.ValidationLimits.MAX_PARTITIONS) {
                return KafkaTopicConstants.ValidationErrors.PARTITIONS_OUT_OF_RANGE + ":" + intent.getPartitions();
            }
        }

        // Validate replication factor range
        if (intent.getReplicationFactor() != null) {
            if (intent.getReplicationFactor() < KafkaTopicConstants.ValidationLimits.MIN_REPLICATION_FACTOR ||
                intent.getReplicationFactor() > KafkaTopicConstants.ValidationLimits.MAX_REPLICATION_FACTOR) {
                return KafkaTopicConstants.ValidationErrors.REPLICATION_OUT_OF_RANGE + ":" + intent.getReplicationFactor();
            }
        }

        return null; // Valid
    }

    /**
     * Validates that topic name is provided for actions that require it.
     */
    private String validateTopicNameRequired(ParsedIntent intent, String action) {
        if (intent.getTopicName() == null || intent.getTopicName().trim().isEmpty()) {
            return "MISSING_TOPIC_NAME_FOR_" + action.toUpperCase();
        }
        return null;
    }
}
