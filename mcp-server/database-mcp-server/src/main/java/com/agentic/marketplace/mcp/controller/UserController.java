package com.agentic.marketplace.mcp.controller;

import com.agentic.marketplace.mcp.model.PrivilegeRequest;
import com.agentic.marketplace.mcp.model.UserDetails;
import com.agentic.marketplace.mcp.model.UserRequest;
import com.agentic.marketplace.mcp.service.DatabaseService;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    @Autowired
    private DatabaseService databaseService;

    @PostMapping
    public ResponseEntity<AgentResponse> createUser(@RequestBody UserRequest request) {
        try {
            log.info("Received request to create user: {}", request.getUsername());
            
            // Check if user already exists
            if (databaseService.userExists(request.getUsername())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("User already exists: " + request.getUsername())
                                .build());
            }
            
            databaseService.createUser(request);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("User created successfully: " + request.getUsername())
                    .data(request)
                    .build());
        } catch (Exception e) {
            log.error("Error creating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to create user")
                            .error(e.getMessage())
                            .build());
        }
    }

    @GetMapping
    public ResponseEntity<AgentResponse> listUsers() {
        try {
            log.info("Received request to list users");
            List<String> users = databaseService.listUsers();
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Found " + users.size() + " user(s)")
                    .data(users)
                    .build());
        } catch (Exception e) {
            log.error("Error listing users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to list users")
                            .error(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{username}")
    public ResponseEntity<AgentResponse> getUserDetails(@PathVariable("username") String username) {
        try {
            log.info("Received request to get user details: {}", username);
            
            if (!databaseService.userExists(username)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("User not found: " + username)
                                .build());
            }
            
            UserDetails details = databaseService.getUserDetails(username);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("User details retrieved successfully")
                    .data(details)
                    .build());
        } catch (Exception e) {
            log.error("Error getting user details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to get user details")
                            .error(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/{username}")
    public ResponseEntity<AgentResponse> updateUser(
            @PathVariable("username") String username,
            @RequestBody UserRequest request) {
        try {
            log.info("Received request to update user: {}", username);
            
            if (!databaseService.userExists(username)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("User not found: " + username)
                                .build());
            }
            
            databaseService.updateUser(username, request);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("User updated successfully: " + username)
                    .data(request)
                    .build());
        } catch (Exception e) {
            log.error("Error updating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to update user")
                            .error(e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<AgentResponse> deleteUser(@PathVariable("username") String username) {
        try {
            log.info("Received request to delete user: {}", username);
            
            if (!databaseService.userExists(username)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("User not found: " + username)
                                .build());
            }
            
            databaseService.deleteUser(username);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("User deleted successfully: " + username)
                    .build());
        } catch (Exception e) {
            log.error("Error deleting user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to delete user")
                            .error(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{username}/grant")
    public ResponseEntity<AgentResponse> grantPrivileges(
            @PathVariable("username") String username,
            @RequestBody PrivilegeRequest request) {
        try {
            log.info("Received request to grant privileges to user: {}", username);
            
            if (!databaseService.userExists(username)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("User not found: " + username)
                                .build());
            }
            
            for (String privilege : request.getPrivileges()) {
                databaseService.grantPrivilege(username, privilege, request.getTableName());
            }
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Privileges granted successfully to: " + username)
                    .data(request)
                    .build());
        } catch (Exception e) {
            log.error("Error granting privileges", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to grant privileges")
                            .error(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{username}/revoke")
    public ResponseEntity<AgentResponse> revokePrivileges(
            @PathVariable("username") String username,
            @RequestBody PrivilegeRequest request) {
        try {
            log.info("Received request to revoke privileges from user: {}", username);
            
            if (!databaseService.userExists(username)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("User not found: " + username)
                                .build());
            }
            
            for (String privilege : request.getPrivileges()) {
                databaseService.revokePrivilege(username, privilege, request.getTableName());
            }
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Privileges revoked successfully from: " + username)
                    .data(request)
                    .build());
        } catch (Exception e) {
            log.error("Error revoking privileges", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to revoke privileges")
                            .error(e.getMessage())
                            .build());
        }
    }
}
