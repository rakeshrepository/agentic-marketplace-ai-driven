package com.agentic.marketplace.agent.constants;

/**
 * Constants for Kafka Topic Management Agent.
 * Centralizes magic strings and numbers to improve maintainability.
 */
public final class KafkaTopicConstants {

    private KafkaTopicConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * Kafka topic actions
     */
    public static final class Actions {
        public static final String CREATE = "create";
        public static final String LIST = "list";
        public static final String DELETE = "delete";
        public static final String DESCRIBE = "describe";

        private Actions() {}
    }

    /**
     * Validation limits for topic configuration
     */
    public static final class ValidationLimits {
        public static final int MIN_PARTITIONS = 1;
        public static final int MAX_PARTITIONS = 10;
        public static final int MIN_REPLICATION_FACTOR = 1;
        public static final int MAX_REPLICATION_FACTOR = 3;

        private ValidationLimits() {}
    }

    /**
     * LLM temperature settings
     */
    public static final class LlmTemperature {
        public static final double EXTRACTION = 0.1;  // Low temperature for consistent parsing
        public static final double GENERATION = 0.7;  // Higher temperature for creative responses

        private LlmTemperature() {}
    }

    /**
     * Validation error codes
     */
    public static final class ValidationErrors {
        public static final String MISSING_TOPIC_NAME_FOR_CREATE = "MISSING_TOPIC_NAME_FOR_CREATE";
        public static final String MISSING_TOPIC_NAME_FOR_DELETE = "MISSING_TOPIC_NAME_FOR_DELETE";
        public static final String MISSING_TOPIC_NAME_FOR_DESCRIBE = "MISSING_TOPIC_NAME_FOR_DESCRIBE";
        public static final String PARTITIONS_OUT_OF_RANGE = "PARTITIONS_OUT_OF_RANGE";
        public static final String REPLICATION_OUT_OF_RANGE = "REPLICATION_OUT_OF_RANGE";
        public static final String UNKNOWN_ACTION = "UNKNOWN_ACTION";
        public static final String INVALID_EMAIL_FORMAT = "INVALID_EMAIL_FORMAT";

        private ValidationErrors() {}
    }
}
