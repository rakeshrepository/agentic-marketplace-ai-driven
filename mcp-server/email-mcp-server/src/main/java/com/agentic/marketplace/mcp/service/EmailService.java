package com.agentic.marketplace.mcp.service;

import com.agentic.marketplace.mcp.config.EmailConfig;
import com.agentic.marketplace.mcp.model.EmailRequest;
import com.agentic.marketplace.mcp.model.EmailResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for sending emails via SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailConfig emailConfig;

    /**
     * Send an email using template or plain text/HTML.
     */
    @Retryable(
            maxAttemptsExpression = "#{${email.retry.max-attempts}}",
            backoff = @Backoff(delayExpression = "#{${email.retry.delay-ms}}")
    )
    public EmailResponse sendEmail(EmailRequest request) {
        log.info("Sending email to: {} with subject: {}", request.getTo(), request.getSubject());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // Set from address
            helper.setFrom(
                    emailConfig.getFrom().getAddress(),
                    emailConfig.getFrom().getName()
            );

            // Set recipients
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());

            // Set CC if provided
            if (request.getCc() != null && request.getCc().length > 0) {
                helper.setCc(request.getCc());
            }

            // Set BCC if provided
            if (request.getBcc() != null && request.getBcc().length > 0) {
                helper.setBcc(request.getBcc());
            }

            // Set reply-to if provided
            if (request.getReplyTo() != null && !request.getReplyTo().isBlank()) {
                helper.setReplyTo(request.getReplyTo());
            }

            // Set email body
            String emailBody = generateEmailBody(request);
            boolean isHtml = request.getTemplateName() != null || request.getHtmlBody() != null;
            helper.setText(emailBody, isHtml);

            // Send email
            mailSender.send(mimeMessage);

            String emailId = UUID.randomUUID().toString();
            log.info("Email sent successfully to {} with ID: {}", request.getTo(), emailId);

            return EmailResponse.builder()
                    .success(true)
                    .message("Email sent successfully")
                    .emailId(emailId)
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

        } catch (MessagingException e) {
            log.error("Failed to send email to: {}", request.getTo(), e);
            return EmailResponse.builder()
                    .success(false)
                    .message("Failed to send email")
                    .error(e.getMessage())
                    .timestamp(Instant.now().toEpochMilli())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error sending email", e);
            return EmailResponse.builder()
                    .success(false)
                    .message("Unexpected error")
                    .error(e.getMessage())
                    .timestamp(Instant.now().toEpochMilli())
                    .build();
        }
    }

    /**
     * Generate email body from template or direct content.
     */
    private String generateEmailBody(EmailRequest request) {
        // Use template if provided
        if (request.getTemplateName() != null && !request.getTemplateName().isBlank()) {
            return processTemplate(request.getTemplateName(), request.getTemplateData());
        }

        // Use HTML body if provided
        if (request.getHtmlBody() != null && !request.getHtmlBody().isBlank()) {
            return request.getHtmlBody();
        }

        // Fall back to text body
        if (request.getTextBody() != null && !request.getTextBody().isBlank()) {
            return request.getTextBody();
        }

        throw new IllegalArgumentException("No email body content provided (template, htmlBody, or textBody)");
    }

    /**
     * Process Thymeleaf template with data.
     */
    private String processTemplate(String templateName, Object templateData) {
        Context context = new Context();
        if (templateData != null) {
            context.setVariable("data", templateData);
            // Also add all map entries as individual variables for convenience
            if (templateData instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) templateData;
                dataMap.forEach(context::setVariable);
            }
        }
        
        return templateEngine.process(templateName, context);
    }

    /**
     * Validate email address format (basic validation).
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
}
