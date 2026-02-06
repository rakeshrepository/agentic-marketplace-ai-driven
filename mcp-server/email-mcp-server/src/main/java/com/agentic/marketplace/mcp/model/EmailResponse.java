package com.agentic.marketplace.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for email operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {

    /**
     * Whether the email was sent successfully.
     */
    private boolean success;

    /**
     * Status message.
     */
    private String message;

    /**
     * Email ID or tracking information (optional).
     */
    private String emailId;

    /**
     * Timestamp when email was sent.
     */
    private Long timestamp;

    /**
     * Error details if sending failed.
     */
    private String error;
}
