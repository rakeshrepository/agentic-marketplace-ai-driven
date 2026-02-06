package com.agentic.marketplace.mcp.controller;

import com.agentic.marketplace.mcp.model.EmailRequest;
import com.agentic.marketplace.mcp.model.EmailResponse;
import com.agentic.marketplace.mcp.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API controller for email operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    /**
     * Send an email.
     */
    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(
            @Valid @RequestBody EmailRequest request,
            BindingResult bindingResult) {

        // Validation errors
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));

            return ResponseEntity.badRequest()
                    .body(buildValidationErrorResponse(errors));
        }

        try {
            EmailResponse response = emailService.sendEmail(request);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(response);
            }
        } catch (Exception e) {
            log.error("Error in sendEmail endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "email-mcp-server");
        health.put("timestamp", Instant.now().toEpochMilli());
        return ResponseEntity.ok(health);
    }

    /**
     * Get available email templates.
     */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> getTemplates() {
        Map<String, Object> templates = new HashMap<>();
        templates.put("available", new String[]{
                "kafka-credentials",
                "plain-text"
        });
        templates.put("description", "Available Thymeleaf email templates");
        return ResponseEntity.ok(templates);
    }

    /**
     * Build validation error response.
     */
    private EmailResponse buildValidationErrorResponse(String errors) {
        return EmailResponse.builder()
                .success(false)
                .message("Validation failed")
                .error(errors)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Build error response.
     */
    private EmailResponse buildErrorResponse(String errorMessage) {
        return EmailResponse.builder()
                .success(false)
                .message("Failed to send email")
                .error(errorMessage)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }
}
