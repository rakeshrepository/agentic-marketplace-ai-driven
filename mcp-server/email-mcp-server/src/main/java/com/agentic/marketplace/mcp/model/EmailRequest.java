package com.agentic.marketplace.mcp.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request model for sending emails via MCP server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    /**
     * Recipient email address.
     */
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String to;

    /**
     * Email subject line.
     */
    @NotBlank(message = "Subject is required")
    private String subject;

    /**
     * Template name to use (e.g., "kafka-credentials", "plain-text").
     */
    private String templateName;

    /**
     * Plain text body (used if no template specified).
     */
    private String textBody;

    /**
     * HTML body (used if no template specified).
     */
    private String htmlBody;

    /**
     * Template data/variables to populate the template.
     */
    private Map<String, Object> templateData;

    /**
     * Optional CC recipients.
     */
    private String[] cc;

    /**
     * Optional BCC recipients.
     */
    private String[] bcc;

    /**
     * Reply-to address (optional).
     */
    @Email(message = "Invalid reply-to email format")
    private String replyTo;
}
