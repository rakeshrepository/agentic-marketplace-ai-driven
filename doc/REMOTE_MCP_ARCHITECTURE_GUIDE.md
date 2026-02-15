# Enterprise Remote MCP Architecture - Kafka Administration via VS Code

**Author:** Senior Architect  
**Date:** February 12, 2026  
**Purpose:** Production-grade architecture for deploying Kafka MCP servers as remote services, accessible exclusively through VS Code IDE

---

## 🎯 Executive Summary

This document provides a complete architecture for deploying Kafka MCP servers as **remote services** that developers access exclusively through **VS Code IDE** on their local laptops.

### What This Is

A remote MCP server infrastructure that allows any developer in the company to manage Kafka clusters by simply talking to the AI assistant in VS Code Copilot — no Kafka CLI tools, no web portals, no terminal commands. Just natural language in the IDE.

### How Developers Interact

**Single entry point:** VS Code on the developer's laptop. No web portals, no CLI tools, no separate applications.

```
Developer's Laptop (VS Code)
    → Opens Copilot Chat
    → Types: "List all topics on dev Kafka cluster"
    → AI assistant calls remote MCP server (behind the scenes)
    → Results displayed in VS Code
```

### Key Design Decisions

- **VS Code only** — All interaction happens inside the IDE. No web portals or external tools
- **PingFederate SSO** — Same company SSO used for Jira, Confluence. Sign in once on install, never again
- **VS Code extension for auth** — ~150 lines. Prompts sign-in on install, caches tokens (keychain + memory), handles refresh silently. Same pattern as GitHub Copilot
- **3 Kafka clusters** — Dev, Staging, Production (architecture supports adding more)
- **Python MCP server** — FastAPI + confluent-kafka, deployed on Kubernetes

### At a Glance

| Aspect | Decision |
|--------|----------|
| **Developer interface** | VS Code IDE (only) |
| **Authentication** | PingFederate SSO (OAuth 2.0) |
| **Auth experience** | Same as GitHub Copilot — sign in once, forget about it |
| **Kafka clusters** | 3 (dev, staging, production) |
| **MCP server language** | Python (FastAPI + confluent-kafka-python) |
| **MCP server deployment** | Kubernetes (AWS EKS) |
| **Onboarding time** | ~5 minutes (install extension → sign in → done) |
| **New user setup** | Install VS Code → Install MCP extension → Sign in via SSO → Start working |
| **Existing user setup** | Install MCP extension → Sign in via SSO → Start working |

---

## 📐 Architecture Diagrams

This section provides visual diagrams for different stakeholders. Each diagram serves a specific purpose:

| Diagram | Primary Audience | Purpose |
|---------|-----------------|---------|
| [High-Level Design (HLD)](#1-high-level-design-hld) | Executive, Architect | System layers, major components, data flow |
| [Industry Standard View](#11-industry-standard-view-layered-architecture) | Enterprise Architect, Tech Lead | Layered architecture with trust boundaries |
| [Low-Level Design (LLD)](#2-low-level-design-lld) | Developer, Architect | Detailed components, APIs, protocols |
| [LLD Component Diagram](#21-lld-component-diagram-c4-level) | Developer, Architect | C4 component level - internal structure |
| [LLD Sequence - Auth](#221-phase-1-initial-authentication-oauth-20-pkce) | Developer, Architect | OAuth 2.0 PKCE authentication flow |
| [LLD Sequence - Request](#222-phase-2-api-request-execution) | Developer, Architect | MCP tool execution through Kong to Kafka |
| [LLD Sequence - Refresh](#223-phase-3-token-refresh-background) | Developer, Architect | Background token refresh cycle |
| [LLD Class Diagram](#23-lld-class-diagram-uml-20) | Developer | UML class - TypeScript/Python structures |
| [LLD Error States](#24-lld-error-states-diagram) | Developer, DevOps | Error codes, causes, recovery actions |
| [LLD State Machine](#25-lld-state-machine-diagram-uml-20) | Developer, Architect | Token, connection, circuit breaker states |
| [LLD Data Flow](#26-lld-data-flow-diagram) | Developer, Architect | Request/response transformations |
| [AWS Infrastructure](#3-aws-infrastructure-topology) | Architect, DevOps | VPC layout, EKS, MSK, network boundaries |
| [Security Boundaries](#4-security-boundary-diagram) | Architect, Security | Auth flow, JWT validation, trust zones |
| [Request Flow](#5-end-to-end-request-flow) | Developer, Architect | Message path from chat to Kafka |
| [Extension Components](#6-vs-code-extension-architecture) | Developer | AuthProvider, TokenManager, internal structure |
| [Token Lifecycle](#7-token-lifecycle-diagram) | Developer, Architect | Access/refresh tokens, 30-day scenario |
| [User Journey](#8-user-journey-map) | Product Owner, Manager | Developer experience from install to daily use |

---

### 1. High-Level Design (HLD)

**Audience:** Executive, Senior Architect  
**Purpose:** Shows overall system architecture with load balancing, multi-pod deployment, and secrets management

![High-Level Design](images/08-hld-detailed.svg)

**Architecture Layers:**
| Layer | Location | Components | Responsibility |
|-------|----------|------------|----------------|
| **Client** | Developer Machine | VS Code (Chat UI, MCP Client, Auth Extension) | User interface, tool invocation, authentication |
| **LLM** | GitHub Cloud (External) | GitHub Copilot (GPT-4) | Intent recognition, tool selection, response formatting |
| **Load Balancer** | AWS | Application Load Balancer (ALB) | TLS termination, external entry, health checks |
| **API Gateway** | AWS EKS | Kong Gateway | JWT validation, rate limiting, path-based routing |
| **K8s Services** | AWS EKS | ClusterIP Services | Internal load balancing to pods (round-robin) |
| **MCP Servers** | AWS EKS | kafka-mcp-{env} pods | Business logic, Kafka operations |
| **Data** | AWS | Amazon MSK clusters | Message streaming, persistence |
| **Identity** | Corporate | PingFederate | OAuth 2.0/OIDC, token issuance |
| **Secrets** | GitHub + AWS | GitHub Secrets, AWS Secrets Manager | Credential storage |

**Load Balancing Explained (3 Levels):**

| Level | Component | What It Does | Why Needed |
|-------|-----------|--------------|------------|
| **L1: ALB** | Application Load Balancer | TLS termination (HTTPS→HTTP), external entry point, AWS DDoS protection, health checks | Single DNS entry (mcp.company.com), managed SSL certs |
| **L2: Kong** | API Gateway | JWT validation, rate limiting (100/min/user), **path-based routing** (`/kafka/dev/*` → dev service) | Security & routing - decides WHICH environment |
| **L3: K8s Service** | ClusterIP Service | **Round-robin** to pods within same deployment | Spreads load across multiple pods of same env |

```
Request: POST /kafka/prod/mcp

Internet → ALB (TLS termination)
              ↓
         Kong (JWT check, route to svc/kafka-mcp-prod)
              ↓
         svc/kafka-mcp-prod (round-robin)
              ↓
         ┌────┼────┐
         ↓    ↓    ↓
       pod-1 pod-2 pod-3  ← Load distributed here
```

**Request Flow (numbered in diagram):**
| Step | From | To | Description |
|------|------|-----|-------------|
| 1 | Developer | Copilot Chat UI | Natural language: "List topics on prod" |
| 2 | VS Code | GitHub Copilot (Cloud) | Send prompt to LLM |
| 3 | Copilot | MCP Client | LLM decides: call `list_topics` tool, env=prod |
| 4 | MCP Client | Auth Extension | Get Bearer token from memory |
| 5 | Extension | ALB → Kong → MCP | HTTPS request with JWT |

**Environment Pod Configuration:**
| Environment | Pods | MSK Brokers | Purpose |
|-------------|------|-------------|---------|
| **dev** | 2 | 3 | Development & testing |
| **sit** | 2 | 3 | System integration testing |
| **uat** | 2 | 3 | User acceptance testing |
| **prod** | 3 | 6 | Production (HA) |

**Secrets Management:**
| Secret Type | Storage Location | Access Method |
|-------------|-----------------|---------------|
| `PING_CLIENT_ID` | GitHub Secrets | CI/CD → K8s Secret |
| `PING_CLIENT_SECRET` | GitHub Secrets | CI/CD → K8s Secret |
| `KAFKA_SASL_USERNAME` | GitHub Secrets | CI/CD → K8s Secret |
| `kafka/{env}/credentials` | AWS Secrets Manager | Runtime SDK fetch |
| `kafka/{env}/truststore` | AWS Secrets Manager | Runtime SDK fetch |

**GitHub Secrets Setup:**
```yaml
# .github/workflows/deploy.yml
env:
  PING_CLIENT_ID: ${{ secrets.PING_CLIENT_ID }}
  PING_CLIENT_SECRET: ${{ secrets.PING_CLIENT_SECRET }}
  KAFKA_SASL_USERNAME: ${{ secrets.KAFKA_SASL_USERNAME }}
```

**AWS Secrets Manager Structure:**
```
kafka/dev/credentials    → {"username": "...", "password": "..."}
kafka/sit/credentials    → {"username": "...", "password": "..."}
kafka/uat/credentials    → {"username": "...", "password": "..."}
kafka/prod/credentials   → {"username": "...", "password": "..."}
```

---

### 1.1 Industry Standard View (Layered Architecture)

**Audience:** Enterprise Architect, Technical Lead  
**Purpose:** Standard layered architecture view following enterprise architecture best practices

![Industry Standard HLD](images/10-hld-industry.svg)

**Design Principles Applied:**

| Principle | Implementation | Benefit |
|-----------|---------------|---------|
| **Layered Architecture** | 6 distinct layers (Presentation → Edge → Application → Data) | Clear separation of concerns |
| **Single Responsibility** | Each component has one purpose (ALB=TLS, Kong=Auth, Service=LB) | Easier maintenance, testing |
| **Defense in Depth** | WAF → ALB → Kong JWT → SASL_SSL | Multiple security checkpoints |
| **12-Factor App** | Config in env vars, stateless pods, backing services | Cloud-native scalability |
| **Cross-Cutting Concerns** | Security & Operations as separate layer | Centralized observability, secrets |

**Layer Definitions (Top-Down):**

| # | Layer | Components | Protocol | Responsibility |
|---|-------|------------|----------|----------------|
| 1 | **Presentation** | VS Code IDE (Copilot Chat, MCP Client, Auth Extension) | — | User interface, authentication |
| 2 | **External Services** | GitHub Copilot (LLM), PingFederate (IdP) | HTTPS | AI reasoning, identity management |
| 3 | **Edge (DMZ)** | ALB + AWS WAF | HTTPS→HTTP | TLS termination, DDoS protection, rate limiting |
| 4 | **Application** | Kong Gateway + EKS Compute | HTTP, gRPC | API security, routing, business logic |
| 5 | **Data** | Amazon MSK (Kafka) | SASL_SSL | Message persistence, streaming |
| 6 | **Security & Ops** | Secrets Manager, IAM, CloudWatch | SDK | Credentials, RBAC, observability |

**Trust Boundaries:**

```
┌─────────────────────────────────────────────────────────────────┐
│ TRUST ZONE 1: Developer Machine                                 │
│   VS Code + Extensions (user's credentials)                     │
├─────────────────────────────────────────────────────────────────┤
│ TRUST ZONE 2: External SaaS                                     │
│   GitHub Copilot (Microsoft managed)                            │
│   PingFederate (Corporate managed)                              │
├─────────────────────────────────────────────────────────────────┤
│ TRUST ZONE 3: AWS DMZ (Public Subnet)                           │
│   ALB + WAF (internet-facing, TLS only)                         │
├─────────────────────────────────────────────────────────────────┤
│ TRUST ZONE 4: AWS Private (Private Subnet)                      │
│   Kong + EKS + MSK (no direct internet access)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 2. Low-Level Design (LLD)

**Audience:** Developer, Architect  
**Purpose:** Shows detailed component interactions, APIs, and protocols

![Low-Level Design](images/09-lld.svg)

**Component Details:**

| Component | Internal Structure | Key APIs/Protocols |
|-----------|-------------------|-------------------|
| **MCP Auth Extension** | AuthProvider → TokenManager → ConfigManager | `getSessions()`, `createSession()` |
| **Token Storage** | Memory (fast) + Keychain (persistent) | ~0ms read from memory cache |
| **Kong JWT Plugin** | RS256 verify → aud check → exp check | JWKS cached 5 min |
| **Kafka MCP Server** | HTTP endpoint → Tool router → KafkaAdminClient | `list_topics`, `describe_topic`, `create_topic` |
| **Request Headers** | `Authorization: Bearer JWT` → `X-User-Email`, `X-User-Teams` | JWT stripped at gateway |

---

### 2.1 LLD Component Diagram (C4 Level)

**Audience:** Developer, Architect  
**Purpose:** Internal structure of each system component following C4 Component diagram standards

![LLD Component Diagram](images/11-lld-component.svg)

**Component Breakdown:**

| System | Module | Components |
|--------|--------|------------|
| **VS Code Extension** | Core | `activate()`, `deactivate()` |
| | Authentication | `AuthenticationProvider`, `AuthSession` |
| | Token | `TokenManager`, `TokenStorage` |
| | Config | `McpConfigManager`, `McpConfig` |
| | UI | `StatusBarItem`, Commands |
| **Kong Gateway** | Plugins | CORS → JWT → RateLimit → RequestTransformer |
| | Routing | `/kafka/{env}/*` → `kafka-mcp-{env}:8080` |
| **MCP Server (Python)** | Web | `McpServer` (FastMCP), `Middleware` |
| | Service | `ToolHandlers`, `KafkaAdminService` |
| | Kafka | `confluent_kafka.AdminClient`, `KafkaConfig` |
| | Model | `ToolRequest`, `ToolResponse`, `TopicInfo` |

---

### 2.2 LLD Sequence Diagrams (UML 2.0)

**Audience:** Developer, Architect  
**Purpose:** Step-by-step message flow with timing for authentication and request execution

The sequence flow is split into three focused diagrams for clarity:

---

#### 2.2.1 Phase 1: Initial Authentication (OAuth 2.0 PKCE)

**Trigger:** First request when no valid token exists  
**Duration:** 3-10 seconds (depends on MFA speed)

![Authentication Sequence](images/12a-sequence-auth.svg)

**Key Steps:**
1. Developer types natural language prompt
2. GitHub Copilot parses intent, identifies MCP tool
3. MCP Client requests authentication from Extension
4. TokenManager checks memory cache (empty) → checks Keychain (empty)
5. Initiates OAuth 2.0 PKCE flow:
   - Generate `code_verifier` (43-128 random chars)
   - Compute `code_challenge` = SHA256(verifier)
   - Open browser to PingFederate
6. User authenticates (credentials + MFA)
7. PingFederate redirects with authorization code
8. Extension exchanges code + verifier for tokens
9. Access token → memory, Refresh token → Keychain
10. Schedule background refresh at 50-minute intervals

**Security Controls:**
| Control | Purpose |
|---------|---------|
| PKCE | Prevents authorization code interception |
| Keychain | Encrypted storage for refresh token |
| Memory-only access token | Never persisted to disk |

---

#### 2.2.2 Phase 2: API Request Execution

**Trigger:** Every tool invocation after authentication  
**Duration:** 150-500ms (P95)

![Request Execution Sequence](images/12b-sequence-request.svg)

**Key Steps:**
1. MCP Client gets token from memory (~0ms)
2. Reads server URL from `~/.vscode/mcp.json`
3. Sends HTTPS POST to ALB with Bearer JWT
4. ALB terminates TLS, forwards to Kong
5. Kong plugin chain executes:
   - CORS validation
   - JWT validation (JWKS from PingFederate)
   - Rate limiting (100/min/user)
   - Claims extraction → headers
   - Route to environment service
6. K8s Service load-balances to pod (round-robin)
7. MCP Server processes request:
   - Parse JSON-RPC 2.0
   - Route to tool handler
   - Execute Kafka AdminClient operation
8. Response returns through same path
9. LLM formats result as natural language

**Latency Breakdown:**
| Component | Duration |
|-----------|----------|
| Token from memory | ~0ms |
| TLS + ALB | 5-15ms |
| Kong validation | 2-10ms |
| K8s routing | 1-5ms |
| MCP processing | 10-50ms |
| Kafka operation | 50-200ms |
| **Total (P95)** | **150-500ms** |

---

#### 2.2.3 Phase 3: Token Refresh (Background)

**Trigger:** Background timer fires 50 minutes after token issuance  
**Duration:** 200-500ms (transparent to user)

![Token Refresh Sequence](images/12c-sequence-refresh.svg)

**Key Steps:**
1. Refresh timer fires at 50 minutes (10 min before expiry)
2. TokenManager retrieves refresh token from Keychain
3. Sends refresh request to PingFederate
4. On success:
   - Update access token in memory
   - Store new refresh token in Keychain
   - Reschedule timer (50 min)
   - Update status bar (✅)
5. On failure:
   - **Token expired (>30 days):** Clear tokens, user must re-auth
   - **Token revoked:** Clear tokens, user must re-auth
   - **Server error:** Retry with backoff, keep using cached token

**Why 50 minutes (not 60)?**
| Scenario | Token Age | Action |
|----------|-----------|--------|
| Normal | 50 min | Refresh (10 min buffer) |
| Refresh fails | 50-60 min | Retry with backoff |
| Still failing | 60 min | Token expires, force re-auth |

**Error Handling:**
| Error | HTTP | Recovery |
|-------|------|----------|
| Token expired | 400 | Clear tokens, re-authenticate |
| Token revoked | 400 | Clear tokens, re-authenticate |
| Server unavailable | 503 | Retry in 2 min, use cached token |

---

**Combined Sequence (Reference):**

For a single combined view of all phases, see: ![Combined Sequence](images/12-lld-sequence.svg)

---

### 2.3 LLD Class Diagram (UML 2.0)

**Audience:** Developer  
**Purpose:** TypeScript/Python class structures with methods, properties, and relationships

![LLD Class Diagram](images/13-lld-class.svg)

**Key Classes:**

| Namespace | Class | Responsibility |
|-----------|-------|---------------|
| **VS Code Extension** | `McpAuthProvider` | Implements VS Code AuthenticationProvider interface |
| | `TokenManager` | Handles token storage, refresh scheduling, PKCE |
| | `McpConfigManager` | Watches and updates `~/.vscode/mcp.json` |
| | `StatusBarManager` | Updates status bar UI based on auth state |
| **Data Types** | `AuthSession` | VS Code session interface |
| | `TokenResponse` | OAuth token response from PingFed |
| | `McpConfig` | mcp.json file structure |
| **MCP Server (Python)** | `KafkaMcpServer` | FastMCP server with tool handlers |
| | `ToolHandlers` | `@mcp.tool()` decorated functions |
| | `KafkaAdminService` | confluent-kafka AdminClient wrapper |
| **Server Types** | `McpRequest/Response` | JSON-RPC 2.0 dataclasses |
| | `TopicInfo/TopicSpec` | Kafka topic dataclasses |

---

### 2.4 LLD Error States Diagram

**Audience:** Developer, DevOps  
**Purpose:** Comprehensive error handling with codes, causes, and recovery actions

![LLD Error States Diagram](images/14-lld-error-states.svg)

**Error Code Reference:**

| Range | Category | Examples |
|-------|----------|----------|
| **E0xx** | Authentication | E001 Network Unreachable, E002 Invalid Credentials, E003 MFA Failed |
| **E1xx** | Token Refresh | E101 Invalid Grant (expired), E102 Token Revoked, E103 Server Unavailable |
| **E2xx** | Gateway (Kong) | E201 401 Unauthorized, E203 429 Rate Limited, E206 504 Timeout |
| **E3xx** | MCP Server | E301 Invalid Tool, E303 Kafka Unreachable, E306 ACL Denied |

**Recovery Actions:**

| Action Type | Symbol | Description |
|-------------|--------|-------------|
| **Retry** | 🔄 | Automatic retry with backoff |
| **User Action** | 👤 | Requires user intervention |
| **Alert** | ⚠️ | Notify DevOps/Admin |

---

### 2.5 LLD State Machine Diagram (UML 2.0)

**Audience:** Developer, Architect  
**Purpose:** State transitions for tokens, connections, and circuit breakers

![LLD State Machine Diagram](images/15-lld-state-machine.svg)

**State Machines:**

| Machine | States | Key Transitions |
|---------|--------|-----------------|
| **Token Lifecycle** | NoToken, Authenticating, TokenValid, Refreshing, TokenExpired | Sign In → Auth → Valid → Refresh cycle |
| **Connection Lifecycle** | Disconnected, Connecting, Connected (Idle/Requesting), Error | Tool invoked → Connect → Request → Idle timeout |
| **Circuit Breaker** | Closed, Open, HalfOpen | 5 failures → Open (30s) → HalfOpen → Test |
| **Kafka Connection** | Init, Connecting, Ready, Error, Closed | Config → SASL_SSL → Ready or Error |

**Circuit Breaker Configuration:**

| Parameter | Value | Purpose |
|-----------|-------|---------|
| Failure threshold | 5 | Failures before trip |
| Reset timeout | 30s | Time before half-open |
| Test requests | 1 | Requests in half-open |

---

### 2.6 LLD Data Flow Diagram

**Audience:** Developer, Architect  
**Purpose:** Request/response data transformations through the system

![LLD Data Flow Diagram](images/16-lld-data-flow.svg)

**Data Transformations:**

| Stage | Input | Output |
|-------|-------|--------|
| **LLM Parsing** | "List all topics on production" | `{tool: "list_topics", arguments: {environment: "prod"}}` |
| **HTTP Request** | Tool call + JWT | POST /kafka/prod/mcp with Authorization header |
| **Kong Transform** | JWT claims | X-User-Email, X-User-Teams headers (JWT stripped) |
| **MCP Processing** | `tools/call` request | Kafka AdminClient.listTopics() |
| **Response Format** | `Set<TopicListing>` | MCP Response with content array |
| **LLM Formatting** | MCP Response | "Found 3 topics: orders, payments, notifications" |

**Request/Response Structures:**

```json
// MCP Request
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "method": "tools/call",
  "params": {
    "name": "list_topics",
    "arguments": {}
  }
}

// MCP Response
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [{
      "type": "text",
      "text": "Topics: orders, payments, notifications"
    }]
  }
}
```

---

### 3. AWS Infrastructure Topology

**Audience:** Senior Architect, DevOps, Security  
**Purpose:** Shows VPC structure, network isolation, and component placement

![AWS Infrastructure Topology](images/01-aws-infra.svg)

**Key Points:**
- ALB terminates TLS, forwards HTTP to Kong
- Kong validates JWT using cached JWKS (no per-request SSO calls)
- Each MCP Deployment can ONLY reach its assigned MSK cluster (NetworkPolicy)
- MSK clusters in separate subnet, not directly accessible from internet

---

### 4. Security Boundary Diagram

**Audience:** Senior Architect, Security Team  
**Purpose:** Shows trust zones, authentication boundaries, and where validation occurs

![Security Boundary Diagram](images/02-security.svg)

**Security Model:**
1. **All auth happens at Kong** — single point of control
2. **MCP servers have NO auth code** — cannot be bypassed
3. **JWKS cached** — PingFederate outage doesn't break existing sessions
4. **JWT stripped before MCP** — servers never see tokens

---

### 5. End-to-End Request Flow

**Audience:** Developer, Architect  
**Purpose:** Shows the complete message path with timing

![End-to-End Request Flow](images/03-request-flow.svg)

**Timing Breakdown:**
| Step | Duration |
|------|----------|
| Intent recognition | ~50ms |
| Token from memory | ~0ms |
| TLS + network | ~20ms |
| Kong JWT validation | ~1ms |
| MCP processing | ~10ms |
| Kafka operation | ~100-300ms |
| **Total** | **~200-400ms** |

---

### 6. VS Code Extension Architecture

**Audience:** Developer (Extension Team)  
**Purpose:** Shows internal components and data flow

![VS Code Extension Architecture](images/04-extension.svg)

**Component Responsibilities:**
| Component | Lines of Code | Responsibility |
|-----------|---------------|----------------|
| AuthenticationProvider | ~150 | VS Code auth API integration |
| TokenManager | ~80 | Token storage, refresh, lifecycle |
| McpConfigManager | ~50 | mcp.json generation |
| StatusBar | ~30 | UI feedback |
| **Total** | **~310** | Complete extension |

---

### 7. Token Lifecycle Diagram

**Audience:** Developer, Architect  
**Purpose:** Shows token states, refresh timing, and 30-day scenario

![Token Lifecycle State Diagram](images/05-token-lifecycle.svg)

**Timeline View:**

![Token Lifecycle Timeline](images/05b-token-timeline.svg)

**Scenarios:**

| Scenario | What Happens | User Action |
|----------|--------------|-------------|
| **Normal use** | Token refreshes every ~50 min | None (silent) |
| **VS Code restart** | Token loaded from keychain | None (automatic) |
| **Machine reboot** | Token loaded from keychain | None (automatic) |
| **30-day inactive** | Refresh token expired | One-click sign-in (~30 sec) |
| **Sign out** | All tokens cleared | Sign in when needed |

---

### 8. User Journey Map

**Audience:** Product Owner, Manager  
**Purpose:** Shows developer experience from install to daily use

![User Journey Map](images/06-user-journey.svg)

**User Experience Metrics:**

| Stage | Time | Friction Level |
|-------|------|----------------|
| **Install** | ~30 sec | ⭐ Very Low |
| **First Sign-In** | ~60 sec | ⭐⭐ Low |
| **Daily Use** | 0 sec | ⭐ None |
| **Token Refresh** | 0 sec | ⭐ None (silent) |
| **30-Day Return** | ~30 sec | ⭐⭐ Low |

**Comparison: Before vs After MCP**

![Before vs After Comparison](images/07-before-after.svg)

---

## 🏗️ High-Level Architecture

### Centralized Auth Pattern: One Extension, One Gateway, Many MCP Servers

The architecture uses a **single VS Code extension** for authentication and a **single API gateway** that validates tokens and routes to the correct MCP server. Individual MCP servers (Kafka, Database, Redis, etc.) contain **zero auth code** — they focus purely on their business logic.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     DEVELOPER WORKSTATION (Laptop)                       │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ VS Code                                                             │ │
│  │                                                                     │ │
│  │  ┌──────────────────────────────────────────────────────────────┐ │ │
│  │  │  MCP Auth Extension (~150 lines)                            │ │ │
│  │  │  • Sign-in prompt on first install (PingFederate SSO)      │ │ │
│  │  │  • Token storage: keychain (persist) + memory (speed)      │ │ │
│  │  │  • getSession() returns from memory (~0ms)                 │ │ │
│  │  │  • Extension handles token refresh silently                │ │ │
│  │  └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                     │ │
│  │  ┌──────────────────────────────────────────────────────────────┐ │ │
│  │  │  ~/.vscode/mcp.json (static URLs only, no tokens)           │ │ │
│  │  │  • mcp.company.com/kafka   → Kafka MCP server               │ │ │
│  │  │  • mcp.company.com/database → Database MCP server           │ │ │
│  │  │  • mcp.company.com/redis   → Redis MCP server               │ │ │
│  │  └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                     │ │
│  │  GitHub Copilot ← Developer talks here                            │ │
│  └─────────────────────────┬──────────────────────────────────────────┘ │
└────────────────────────────┼────────────────────────────────────────────┘
                             │
                             │ MCP Transport: Streamable HTTP
                             │ Protocol: HTTPS (TLS 1.3)
                             │ Header: Authorization: Bearer <JWT>
                             │ Same token for ALL MCP servers
                             ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                         AWS CLOUD INFRASTRUCTURE                         │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │         API GATEWAY (Single Entry Point for ALL MCP servers)     │  │
│  │                                                                    │  │
│  │  1. Validates JWT (signed by PingFederate? Not expired?)         │  │
│  │  2. Extracts user identity & permissions from token              │  │
│  │  3. Rate limiting (per user)                                      │  │
│  │  4. Routes to correct MCP server based on URL path               │  │
│  │  5. Audit logging (who, what, when)                              │  │
│  │                                                                    │  │
│  │  Auth happens HERE — MCP servers don't need any auth code        │  │
│  │                                                                    │  │
│  │  Routes:                                                          │  │
│  │    /kafka/*     → Kafka MCP Server                               │  │
│  │    /database/*  → Database MCP Server                            │  │
│  │    /redis/*     → Redis MCP Server                               │  │
│  │    /s3/*        → (future) S3 MCP Server                         │  │
│  └──────┬─────────────────┬─────────────────┬───────────────────────┘  │
│         │                 │                 │                           │
│  ┌──────▼──────┐  ┌───────▼───────┐  ┌──────▼──────┐                  │
│  │ Kafka MCP   │  │ Database MCP  │  │ Redis MCP   │  (add more       │
│  │ Server      │  │ Server        │  │ Server      │   MCP servers    │
│  │ (Python)    │  │ (Python)      │  │ (Python)    │   in the future) │
│  │             │  │               │  │             │                   │
│  │ NO auth     │  │ NO auth       │  │ NO auth     │                   │
│  │ code!       │  │ code!         │  │ code!       │                   │
│  │             │  │               │  │             │                   │
│  │ Pure Kafka  │  │ Pure DB       │  │ Pure Redis  │                   │
│  │ operations  │  │ operations    │  │ operations  │                   │
│  └──────┬──────┘  └───────┬───────┘  └──────┬──────┘                  │
│         │                 │                 │                           │
│  ┌──────▼──────┐  ┌───────▼───────┐  ┌──────▼──────┐                  │
│  │ Kafka       │  │ PostgreSQL /  │  │ Redis       │                   │
│  │ Clusters    │  │ MySQL / etc.  │  │ Clusters    │                   │
│  │ (3 clusters)│  │               │  │             │                   │
│  │ Dev/Stg/Prod│  │               │  │             │                   │
│  └─────────────┘  └───────────────┘  └─────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘
```

### How Gateway Routes Connect to MCP Tools (Two-Layer Routing)

There are **two separate layers of routing** — this is the critical distinction:

| Layer | What routes | How | Example |
|-------|------------|-----|---------|
| **Layer 1: API Gateway** | URL path → which MCP server **pod** | Kubernetes Ingress path prefix matching | `/kafka/prod/*` → `kafka-mcp-prod-service:8000` |
| **Layer 2: MCP Protocol** | Tool name → which **function** in the MCP server | MCP JSON-RPC protocol (`tools/call`) | `"name": "list_topics"` → `handleListTopics()` |

**These two layers are completely independent:**
- The Gateway doesn't know or care about MCP tools
- The MCP server doesn't know or care about URL paths

```
LAYER 1: Gateway Routing (URL path → which pod)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  VS Code connects to: https://mcp.company.com/kafka/prod
                                                ^^^^^^^^^^^
                                                URL path prefix

  Kong Ingress matches /kafka/prod → routes to kafka-mcp-prod-service:8000
  Kong strips the prefix → MCP server receives requests at /

  The gateway does NOT understand MCP protocol.
  It only does: path prefix match → forward to correct Kubernetes service.


LAYER 2: MCP Protocol (tool name → which function)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Once the HTTP request reaches the MCP server, everything
  follows the MCP JSON-RPC protocol. The MCP server handles:

  Step 1: VS Code sends "initialize" request
  ┌─────────────────────────────────────────────────────┐
  │ { "jsonrpc": "2.0",                                 │
  │   "method": "initialize",                           │
  │   "id": 1,                                          │
  │   "params": { "clientInfo": { "name": "vscode" } }  │
  │ }                                                    │
  └─────────────────────────────────────────────────────┘
  Server responds with capabilities (including "tools" support)

  Step 2: VS Code sends "tools/list" to discover available tools
  ┌─────────────────────────────────────────────────────┐
  │ { "jsonrpc": "2.0",                                 │
  │   "method": "tools/list",                           │
  │   "id": 2                                           │
  │ }                                                    │
  └─────────────────────────────────────────────────────┘
  Server responds with ALL tools it offers:
  ┌─────────────────────────────────────────────────────┐
  │ { "tools": [                                        │
  │     { "name": "list_topics",                        │
  │       "description": "Lists all Kafka topics" },    │
  │     { "name": "describe_topic",                     │
  │       "description": "Get topic details" },         │
  │     { "name": "create_topic",                       │
  │       "description": "Creates a new Kafka topic" }, │
  │     { "name": "delete_topic", ... },                │
  │     { "name": "cluster_overview", ... },            │
  │     { "name": "topic_exists", ... },                │
  │     { "name": "update_topic", ... }                 │
  │   ]                                                  │
  │ }                                                    │
  └─────────────────────────────────────────────────────┘
  VS Code registers these tools in Copilot Chat.
  Now the developer can use them via natural language.

  Step 3: Developer says "List topics on kafka-prod"
  Copilot matches intent → VS Code sends "tools/call":
  ┌─────────────────────────────────────────────────────┐
  │ { "jsonrpc": "2.0",                                 │
  │   "method": "tools/call",                           │
  │   "id": 3,                                          │
  │   "params": {                                       │
  │     "name": "list_topics",   ← tool name            │
  │     "arguments": {}          ← tool parameters      │
  │   }                                                  │
  │ }                                                    │
  └─────────────────────────────────────────────────────┘
  MCP server runs handleListTopics() → returns result.
```

**Full end-to-end flow showing both layers:**

```
Developer types: "List topics on kafka-prod"
        │
        ▼
Copilot Chat matches intent: tool = "list_topics", server = "kafka-prod"
        │
        ▼
VS Code MCP Client reads mcp.json:
  "kafka-prod": { "type": "http", "url": "https://mcp.company.com/kafka/prod" }
        │
        ▼ POST https://mcp.company.com/kafka/prod
          Authorization: Bearer <JWT>
          Body: { "method": "tools/call", "params": { "name": "list_topics" } }
        │
        ▼
┌─ LAYER 1: API Gateway (Kong) ─────────────────────────────────────────┐
│  URL path: /kafka/prod                                                 │
│  ✓ JWT valid (cached JWKS)                                            │
│  ✓ User: developer@company.com                                        │
│  Strip path prefix → forward to kafka-mcp-prod-service:8000          │
│  NOTE: Gateway does NOT parse MCP JSON-RPC body, it doesn't need to. │
│  It just forwards the HTTP request after JWT validation.               │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌─ LAYER 2: MCP Server (Kafka Prod) ────────────────────────────────────┐
│  Receives: { "method": "tools/call", "params": { "name":"list_topics"}}│
│                                                                        │
│  MCP protocol router (JSON-RPC):                                       │
│    "initialize"  → handleInitialize()                                  │
│    "tools/list"  → handleToolsList()    ← returns all 7 tool defs     │
│    "tools/call"  → handleToolCall()     ← dispatches by tool name     │
│    "ping"        → handlePing()                                        │
│                                                                        │
│  handleToolCall() dispatches by name:                                  │
│    "list_topics"      → kafka_admin.list_topics()                     │
│    "describe_topic"   → kafka_admin.describe_topic(topic_name)        │
│    "create_topic"     → kafka_admin.create_topic(topic_name, ...)     │
│    "delete_topic"     → kafka_admin.delete_topic(topic_name)          │
│    "update_topic"     → kafka_admin.update_topic(topic_name, ...)     │
│    "topic_exists"     → kafka_admin.topic_exists(topic_name)          │
│    "cluster_overview" → kafka_admin.cluster_overview()                │
│                                                                        │
│  Result: ["orders", "payments", "users", "inventory"]                  │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
                    Response flows back through Gateway → VS Code
                    Copilot Chat shows: "Found 4 topics: orders, payments,
                    users, inventory"
```

**Key takeaway:** The API Gateway routes by **URL path** (which MCP server pod to forward to). The MCP server routes by **tool name** inside the JSON-RPC body (which function to execute). The gateway never looks at tool names. The MCP server never looks at URL paths. They operate at different layers.

### MCP Server Pod ≠ Microservice (No URL Routes)

An MCP server pod is **NOT** a REST microservice. It does **not** define URL endpoints like `/topics`, `/topics/{id}`, etc. It exposes a **single endpoint** and uses the MCP JSON-RPC protocol internally.

| | Traditional Microservice | MCP Server Pod |
|---|---|---|
| **Endpoints** | Many: `GET /topics`, `POST /topics`, `DELETE /topics/{id}` | **One**: `/` (single endpoint) |
| **Routing** | URL path + HTTP method | `"method"` + `"name"` fields inside JSON body |
| **Protocol** | REST API | JSON-RPC over Streamable HTTP |
| **URL matching** | Yes (path patterns, route handlers) | **No** (all requests go to same endpoint) |

```
REST Microservice (NOT this):         MCP Server (THIS):
                                      
  GET  /api/topics     → list          POST /  ← ALL requests go here
  GET  /api/topics/X   → describe       Body: { "method": "tools/list" }  → return tools
  POST /api/topics     → create         Body: { "method": "tools/call",
  DELETE /api/topics/X → delete                  "params": {"name": "list_topics"} }
                                         Body: { "method": "tools/call",
  Each operation = different URL                  "params": {"name": "create_topic"} }
                                      
                                        All operations = same URL, different JSON body
```

The MCP SDK (Python `mcp` library or Java Spring AI MCP) handles this automatically — it exposes one HTTP endpoint and dispatches internally by the JSON-RPC `method` and tool `name`. The pod just listens on one port (e.g., `8000`) at `/`.

### Why This Design?

| Problem | Solution |
|---------|----------|
| Building auth in every MCP server = duplication | **One gateway validates JWT for ALL servers** |
| One VS Code extension per MCP server = messy | **One extension handles auth for ALL servers** |
| Adding a new MCP server requires auth code | **Deploy new server behind gateway — zero auth code** |
| Token management scattered | **One extension, one token, stored once in OS keychain** |

### Adding a New MCP Server (e.g., S3, Elasticsearch)

When you need a new MCP server in the future:
1. Build the MCP server (pure business logic, no auth code)
2. Deploy behind the same API gateway
3. Add route in gateway: `/s3/*` → S3 MCP Server
4. Update VS Code extension config to show new server
5. **Developers get access immediately** — same token, same sign-in, no new login required

### Multi-Environment UX: How the LLM Handles Environment Selection

Since developers have ONE IDE but access MULTIPLE environments (dev, staging, prod), the LLM must intelligently determine which cluster to target. This works because **each Kafka cluster is a separate MCP server** with a descriptive label.

**What the LLM sees** (from mcp.json server registrations):
```
Server: kafka-dev        Label: "Kafka Dev (us-east-1)"        Tools: list_topics, create_topic, ...
Server: kafka-staging    Label: "Kafka Staging (us-east-1)"    Tools: list_topics, create_topic, ...
Server: kafka-prod       Label: "Kafka Prod (us-east-1)"       Tools: list_topics, create_topic, ...
Server: kafka-prod-eu    Label: "Kafka Prod (eu-west-1)"       Tools: list_topics, create_topic, ...
```

**LLM behavior based on developer intent:**

| Developer says | LLM action |
|---|---|
| "List topics on **prod**" | Matches "prod" → calls `kafka-prod.list_topics` directly |
| "List topics on **prod EU**" | Matches "prod" + "EU" → calls `kafka-prod-eu.list_topics` |
| "Create topic orders on **dev**" | Matches "dev" → calls `kafka-dev.create_topic` |
| "List topics" *(no env specified)* | **Asks the developer:** "Which environment? I can see Dev, Staging, Prod (us-east-1), and Prod (eu-west-1)" |
| "List topics on **all clusters**" | Calls `list_topics` on each server, combines and presents results |
| "Compare topic count across envs" | Calls `list_topics` on all servers, compares counts |

**Why this works automatically (no custom code needed):**
- VS Code's MCP client registers each server's tools with its server name as a namespace
- The LLM can read server labels to understand which environment each represents
- When ambiguous, the LLM naturally asks a clarifying question (same as any LLM chat)
- The tool descriptions can include hints: e.g., `"Lists all Kafka topics on the dev cluster"`

**Best practice for tool descriptions (in the MCP server):**
```
Rather than:     "Lists all Kafka topics"
Use:             "Lists all Kafka topics on the dev cluster (us-east-1)"
```
This gives the LLM enough context to route correctly without asking.

### End-to-End Request Flow: From Chat Message to API Response

This section provides a complete walkthrough of how a developer's natural language request flows through the entire system. Understanding this flow is essential for debugging and extending the architecture.

#### What Happens at Startup (One Time)

When VS Code starts and loads mcp.json, this happens for **each** configured MCP server:

```
┌─ VS Code Startup ──────────────────────────────────────────────────────────┐
│                                                                             │
│  1. MCP Client reads mcp.json and finds servers:                           │
│     - kafka-dev:    https://mcp.company.com/kafka/dev                      │
│     - kafka-prod:   https://mcp.company.com/kafka/prod                     │
│     - database-dev: https://mcp.company.com/database/dev                   │
│                                                                             │
│  2. FOR EACH SERVER, MCP Client connects and discovers tools:              │
│                                                                             │
│     ┌─ kafka-dev (POST /kafka/dev) ─────────────────────────────────────┐  │
│     │ → Send: { "method": "initialize" }                                │  │
│     │ ← Recv: { capabilities: { tools: true } }                         │  │
│     │                                                                    │  │
│     │ → Send: { "method": "tools/list" }                                │  │
│     │ ← Recv: { tools: [list_topics, describe_topic, create_topic,     │  │
│     │                   delete_topic, topic_exists, cluster_overview,   │  │
│     │                   update_topic] }                                  │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│     ┌─ database-dev (POST /database/dev) ───────────────────────────────┐  │
│     │ → Send: { "method": "initialize" }                                │  │
│     │ ← Recv: { capabilities: { tools: true } }                         │  │
│     │                                                                    │  │
│     │ → Send: { "method": "tools/list" }                                │  │
│     │ ← Recv: { tools: [list_tables, describe_table, run_query,        │  │
│     │                   list_schemas, table_exists, explain_query] }    │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  3. Copilot Chat now has a MERGED tool palette:                            │
│                                                                             │
│     From kafka-dev:    list_topics, describe_topic, create_topic, ...     │
│     From kafka-prod:   list_topics, describe_topic, create_topic, ...     │
│     From database-dev: list_tables, describe_table, run_query, ...        │
│                                                                             │
│     Total: 13+ tools available for natural language invocation              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Example 1: "List all topics from dev"

```
┌─ DEVELOPER TYPES ──────────────────────────────────────────────────────────┐
│                                                                             │
│  Copilot Chat: "List all topics from dev"                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ COPILOT'S REASONING ──────────────────────────────────────────────────────┐
│                                                                             │
│  Intent Analysis:                                                           │
│    • "topics" → matches kafka tools (list_topics, describe_topic, etc.)    │
│    • "dev" → matches kafka-dev server                                      │
│    • Action: list → matches list_topics tool                               │
│                                                                             │
│  Decision: Call list_topics on kafka-dev server                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ VS CODE MCP CLIENT ───────────────────────────────────────────────────────┐
│                                                                             │
│  Look up server URL from mcp.json:                                         │
│    kafka-dev → https://mcp.company.com/kafka/dev                           │
│                                                                             │
│  Get JWT token from MCP Auth Extension (from memory cache, ~0ms)           │
│                                                                             │
│  Build HTTP request:                                                        │
│    POST https://mcp.company.com/kafka/dev                                  │
│    Headers:                                                                 │
│      Authorization: Bearer eyJhbGciOiJSUzI1NiIs...                         │
│      Content-Type: application/json                                        │
│    Body:                                                                    │
│      { "jsonrpc": "2.0",                                                   │
│        "method": "tools/call",                                             │
│        "id": 42,                                                           │
│        "params": { "name": "list_topics", "arguments": {} } }              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ KONG API GATEWAY (AWS EKS) ───────────────────────────────────────────────┐
│                                                                             │
│  Receive: POST /kafka/dev                                                  │
│                                                                             │
│  Step 1: JWT Validation                                                    │
│    • Extract token from Authorization header                               │
│    • Validate signature using cached JWKS from PingFederate               │
│    • Check expiry (exp claim)                                              │
│    • ✓ Token valid                                                         │
│                                                                             │
│  Step 2: Route Matching                                                    │
│    • URL path: /kafka/dev                                                  │
│    • Match rule: /kafka/dev/* → kafka-mcp-dev-service:8000                │
│    • Strip prefix: /kafka/dev → /                                          │
│                                                                             │
│  Step 3: Forward                                                           │
│    • Forward to: http://kafka-mcp-dev-service:8000/                        │
│    • Preserve all headers and body                                         │
│                                                                             │
│  NOTE: Gateway does NOT read JSON body. It doesn't know about MCP tools.   │
│  It only validates JWT and routes by URL path.                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ KAFKA MCP SERVER (Pod in EKS) ────────────────────────────────────────────┐
│                                                                             │
│  Receive: POST / (prefix was stripped by gateway)                          │
│                                                                             │
│  Parse JSON-RPC:                                                           │
│    method: "tools/call"                                                    │
│    params.name: "list_topics"                                              │
│    params.arguments: {}                                                     │
│                                                                             │
│  Dispatch:                                                                  │
│    "list_topics" → handleListTopics()                                      │
│                                                                             │
│  Execute:                                                                   │
│    kafka_admin = KafkaAdminClient(bootstrap_servers=["kafka-dev:9092"])    │
│    topics = kafka_admin.list_topics()                                      │
│    → ["orders", "payments", "users", "inventory", "audit-logs"]            │
│                                                                             │
│  Response:                                                                  │
│    { "jsonrpc": "2.0",                                                     │
│      "id": 42,                                                             │
│      "result": { "content": [                                              │
│        { "type": "text",                                                   │
│          "text": "Found 5 topics: orders, payments, users, inventory,     │
│                   audit-logs" }                                             │
│      ] } }                                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                Response flows back through Gateway → VS Code
                                    │
                                    ▼
┌─ COPILOT CHAT DISPLAYS ────────────────────────────────────────────────────┐
│                                                                             │
│  "Found 5 topics on kafka-dev: orders, payments, users, inventory,        │
│   audit-logs"                                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Example 2: "List all tables" (Different MCP Server)

This example shows how the **same JWT token** works for a **different MCP server** (Database instead of Kafka):

```
┌─ DEVELOPER TYPES ──────────────────────────────────────────────────────────┐
│                                                                             │
│  Copilot Chat: "List all tables"                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ COPILOT'S REASONING ──────────────────────────────────────────────────────┐
│                                                                             │
│  Intent Analysis:                                                           │
│    • "tables" → matches database tools (list_tables, describe_table, etc.)│
│    • Environment not specified → use default or ask                        │
│    • Action: list → matches list_tables tool                               │
│                                                                             │
│  Decision: Call list_tables on database-dev server                         │
│  (or ask "Which database environment?" if multiple are configured)         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ VS CODE MCP CLIENT ───────────────────────────────────────────────────────┐
│                                                                             │
│  Look up server URL from mcp.json:                                         │
│    database-dev → https://mcp.company.com/database/dev                     │
│                                         ^^^^^^^^^^^^                        │
│                       DIFFERENT path prefix than Kafka                      │
│                                                                             │
│  Get JWT token from MCP Auth Extension (SAME token, from memory)           │
│                                                                             │
│  Build HTTP request:                                                        │
│    POST https://mcp.company.com/database/dev                               │
│    Headers:                                                                 │
│      Authorization: Bearer eyJhbGciOiJSUzI1NiIs...  ← SAME token           │
│      Content-Type: application/json                                        │
│    Body:                                                                    │
│      { "jsonrpc": "2.0",                                                   │
│        "method": "tools/call",                                             │
│        "id": 43,                                                           │
│        "params": { "name": "list_tables", "arguments": {} } }              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ KONG API GATEWAY (AWS EKS) ───────────────────────────────────────────────┐
│                                                                             │
│  Receive: POST /database/dev                                               │
│                                                                             │
│  Step 1: JWT Validation                                                    │
│    • SAME validation as before (JWT is user identity, not server-specific) │
│    • ✓ Token valid                                                         │
│                                                                             │
│  Step 2: Route Matching                                                    │
│    • URL path: /database/dev                                               │
│    • Match rule: /database/dev/* → database-mcp-dev-service:8000          │
│                   ^^^^^^^^^^^^                                              │
│                   DIFFERENT route than /kafka/dev                          │
│    • Strip prefix: /database/dev → /                                       │
│                                                                             │
│  Step 3: Forward                                                           │
│    • Forward to: http://database-mcp-dev-service:8000/                     │
│                       ^^^^^^^^^^^^^^^^^^^^^^^^^^^                          │
│                       DIFFERENT Kubernetes service                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─ DATABASE MCP SERVER (Different Pod) ──────────────────────────────────────┐
│                                                                             │
│  Parse JSON-RPC:                                                           │
│    method: "tools/call"                                                    │
│    params.name: "list_tables"                                              │
│                                                                             │
│  Execute:                                                                   │
│    db = PostgresClient(host="postgres-dev", database="main")               │
│    tables = db.query("SELECT table_name FROM information_schema.tables")   │
│    → ["users", "orders", "products", "payments", "audit_log"]              │
│                                                                             │
│  Response:                                                                  │
│    { "jsonrpc": "2.0",                                                     │
│      "id": 43,                                                             │
│      "result": { "content": [                                              │
│        { "type": "text",                                                   │
│          "text": "Found 5 tables: users, orders, products, payments,      │
│                   audit_log" }                                              │
│      ] } }                                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Visual Summary: Multi-Server Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           VS CODE + COPILOT CHAT                            │
│                                                                             │
│  mcp.json:                          Copilot's Tool Palette:                │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────┐│
│  │ kafka-dev:    /kafka/dev    │    │ kafka-dev:                          ││
│  │ kafka-prod:   /kafka/prod   │    │   • list_topics                     ││
│  │ database-dev: /database/dev │    │   • describe_topic                  ││
│  │ s3-dev:       /s3/dev       │    │   • create_topic                    ││
│  └─────────────────────────────┘    │   • delete_topic                    ││
│                                      │   • ...                             ││
│  Auth Extension:                     │ database-dev:                       ││
│  ┌─────────────────────────────┐    │   • list_tables                     ││
│  │ JWT Token (memory cache)    │    │   • describe_table                  ││
│  │ ✓ Same token for ALL calls  │    │   • run_query                       ││
│  └─────────────────────────────┘    │   • ...                             ││
│                                      │ s3-dev:                             ││
│                                      │   • list_buckets                    ││
│                                      │   • ...                             ││
│                                      └─────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTPS (JWT in header)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KONG API GATEWAY (AWS EKS)                          │
│                                                                             │
│  URL Path Routing Rules:                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ /kafka/dev/*     → kafka-mcp-dev-service:8000                       │   │
│  │ /kafka/prod/*    → kafka-mcp-prod-service:8000                      │   │
│  │ /database/dev/*  → database-mcp-dev-service:8000                    │   │
│  │ /s3/dev/*        → s3-mcp-dev-service:8000                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  JWT Validation: ✓ (same for all routes, validates user identity)          │
│                                                                             │
└───────────┬───────────────┬────────────────┬───────────────┬────────────────┘
            │               │                │               │
            ▼               ▼                ▼               ▼
     ┌──────────┐    ┌──────────┐     ┌──────────┐    ┌──────────┐
     │  Kafka   │    │  Kafka   │     │ Database │    │    S3    │
     │   MCP    │    │   MCP    │     │   MCP    │    │   MCP    │
     │  (Dev)   │    │  (Prod)  │     │  (Dev)   │    │  (Dev)   │
     └────┬─────┘    └────┬─────┘     └────┬─────┘    └────┬─────┘
          │               │                │               │
          ▼               ▼                ▼               ▼
     ┌──────────┐    ┌──────────┐     ┌──────────┐    ┌──────────┐
     │  Kafka   │    │  Kafka   │     │ Postgres │    │   AWS    │
     │  Broker  │    │  Broker  │     │    DB    │    │    S3    │
     │  (Dev)   │    │  (Prod)  │     │  (Dev)   │    │  (Dev)   │
     └──────────┘    └──────────┘     └──────────┘    └──────────┘
```

#### Key Insights from These Flows

| Aspect | How It Works |
|--------|--------------|
| **Tool Discovery** | Each MCP server advertises its own tools. Copilot merges all tools into one palette. |
| **Server Selection** | Copilot matches user intent (keywords like "topics", "tables") to correct MCP server. |
| **Route Decision** | URL path prefix (not tool name) determines which MCP server receives the request. |
| **Token Reuse** | Same JWT token authenticates all requests—it represents the user, not the server. |
| **Gateway Role** | Validates JWT, routes by URL path, forwards blindly—never parses MCP protocol. |
| **MCP Server Role** | Parses JSON-RPC, dispatches by tool name, executes against backend system. |

---

## 🔐 1. Security Architecture: IDE to MCP Server

### How It Works (Same Pattern as GitHub Copilot)

The authentication approach is **identical to how GitHub Copilot works in VS Code today**:
- GitHub Copilot: Sign in once → browser opens GitHub → token stored → every request sends token to GitHub API
- MCP Kafka: Sign in once → browser opens PingFederate SSO → token stored → every request sends token to MCP Server

**Developers already do this every day with Copilot — same experience, different backend.**

**Key difference from Copilot:** This ONE extension handles authentication for ALL MCP servers (Kafka, Database, Redis, future servers). One sign-in = access to everything. No separate auth per server.

### Authentication Flow (PingFederate SSO + OAuth 2.0)

```
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 1: First-Time Sign-In (Immediate on Install)                   │
└──────────────────────────────────────────────────────────────────────┘

  1. Developer installs MCP Auth extension
  2. Extension activates → immediately prompts: "Sign in to Company SSO"
  3. Developer clicks "Sign In" → browser opens → PingFederate SSO
  4. Developer logs in (same credentials as Jira, Confluence)
  5. Tokens received:
     - Access Token (JWT, 1 hour TTL) → stored in memory + keychain
     - Refresh Token (30 days TTL) → stored in keychain
  6. Done. Developer never sees this again.

┌──────────────────────────────────────────────────────────────────────┐
│ STEP 2: Every MCP Request (In-Memory, Instant)                      │
└──────────────────────────────────────────────────────────────────────┘

  Developer types: "List Kafka topics on dev"

  1. VS Code calls: getSession('company-sso')
  2. Extension returns session from MEMORY cache (no keychain read)
  3. VS Code attaches: Authorization: Bearer <JWT>
  4. Request sent → Results displayed

  Why it's fast:
    - Keychain read: once at extension startup
    - Memory cache: used for all requests
    - getSession(): returns from memory (~0ms)

┌──────────────────────────────────────────────────────────────────────┐
│ STEP 3: Token Refresh (Extension Handles It)                        │
└──────────────────────────────────────────────────────────────────────┘

  When getSession() is called and access token is expired:
    1. Extension uses refresh token to get new access token
    2. Updates memory cache + keychain
    3. Returns valid session

  If refresh token expires (30 days inactive):
    - Extension prompts: "Session expired. Sign in again."
    - Developer clicks → browser → SSO → 30 seconds
```

### Side-by-Side: GitHub Copilot vs MCP Kafka Auth

| Aspect | GitHub Copilot | MCP Kafka (our approach) |
|--------|----------------|---------------------------|
| **First sign-in** | Immediate on install | Immediate on install |
| **SSO Provider** | GitHub OAuth | PingFederate OAuth 2.0 + PKCE |
| **Token storage** | Keychain (persistence) + Memory (speed) | Keychain (persistence) + Memory (speed) |
| **getSession() performance** | Returns from memory (~0ms) | Returns from memory (~0ms) |
| **Token refresh** | Extension handles silently | Extension handles silently |
| **Extension code** | ~200 lines | ~150 lines |

### New Users vs Existing Users

| Scenario | What Happens |
|----------|-------------|
| **New VS Code user** | Install extension → Sign in once → Done |
| **Existing VS Code user** | Install extension → Sign in once → Done (no impact on other extensions) |
| **User returning after 30 days** | "Sign in again" prompt → 30 seconds |

### Token Lifecycle: Why "30 Days Inactive" Requires Re-Sign-In

When you sign in, PingFederate issues **two tokens** with different lifetimes:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TWO TOKENS, TWO PURPOSES                            │
│                                                                             │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────┐│
│  │      ACCESS TOKEN           │    │       REFRESH TOKEN                 ││
│  │                             │    │                                     ││
│  │  • TTL: 1 hour              │    │  • TTL: 30 days                     ││
│  │  • Used for: API requests   │    │  • Used for: Getting new access    ││
│  │  • Sent to: Kong Gateway    │    │    tokens when they expire         ││
│  │                             │    │  • Sent to: PingFederate only      ││
│  └─────────────────────────────┘    └─────────────────────────────────────┘│
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Normal Flow (User Active Daily):**

```
Day 1, 9:00 AM - User signs in
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Access Token (expires 10:00 AM)
  → Refresh Token (expires Day 31)

Day 1, 10:05 AM - User runs "list topics" (access token expired)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ┌─ Extension (automatic, silent) ──────────────────────────────────────────┐
  │                                                                           │
  │  1. Extension detects: "Access token expired"                            │
  │                                                                           │
  │  2. Extension calls PingFederate: POST /token                            │
  │     Body: { grant_type: "refresh_token", refresh_token: "..." }          │
  │                                                                           │
  │  3. PingFederate checks: Is refresh token valid? YES (29 days left)     │
  │                                                                           │
  │  4. PingFederate returns: NEW access token (expires 11:05 AM)           │
  │                                                                           │
  │  5. Extension updates memory cache + keychain                            │
  │                                                                           │
  │  USER SEES NOTHING — completely silent, no browser, no prompt           │
  │                                                                           │
  └───────────────────────────────────────────────────────────────────────────┘

Day 2, Day 3, ... Day 29 - Same pattern
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Access token expires every hour → Extension silently refreshes
  Refresh token still valid → User never sees sign-in prompt
```

**The 30-Day Inactive Scenario:**

```
Day 1, 9:00 AM - User signs in
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Access Token (expires 10:00 AM)
  → Refresh Token (expires Day 31)

Day 2 to Day 35 - User goes on vacation, doesn't open VS Code
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Day 35, 9:00 AM - User opens VS Code, runs "list topics"
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ┌─ Extension tries to refresh ─────────────────────────────────────────────┐
  │                                                                           │
  │  1. Extension detects: "Access token expired"                            │
  │                                                                           │
  │  2. Extension tries: POST /token with refresh_token                      │
  │                                                                           │
  │  3. PingFederate responds: 401 INVALID_GRANT                             │
  │     "Refresh token expired" (it was 30 days, now it's day 35)           │
  │                                                                           │
  │  4. Extension: "I can't auto-refresh. Must ask user to sign in."        │
  │                                                                           │
  └───────────────────────────────────────────────────────────────────────────┘

  ┌─ VS Code shows notification ─────────────────────────────────────────────┐
  │                                                                           │
  │  ┌──────────────────────────────────────────────────────────────────┐    │
  │  │ ℹ️  Company SSO                                                   │    │
  │  │                                                                    │    │
  │  │  Your session has expired. Please sign in again.                 │    │
  │  │                                                                    │    │
  │  │  [ Sign In ]  [ Later ]                                          │    │
  │  └──────────────────────────────────────────────────────────────────┘    │
  │                                                                           │
  │  This is VS Code's built-in authentication notification.                 │
  │  Triggered when getSessions() returns empty but session is needed.      │
  │                                                                           │
  └───────────────────────────────────────────────────────────────────────────┘

  User clicks "Sign In" → Browser opens → SSO login → New tokens → Done
```

**Token Timeline:**

```
        Day 1              Day 15            Day 30           Day 35
          │                  │                  │                │
          ▼                  ▼                  ▼                ▼
    ┌──────────────────────────────────────────────────────────────────┐
    │                    REFRESH TOKEN VALID                           │
    │   (extension can silently get new access tokens — no prompts)   │
    └──────────────────────────────────────────────────────────────────┘
                                                │
                                                │ EXPIRES
                                                ▼
                                          ┌──────────────────────┐
                                          │  REFRESH TOKEN       │
                                          │  EXPIRED             │
                                          │                      │
                                          │  Extension cannot    │
                                          │  auto-refresh        │
                                          │                      │
                                          │  → Prompt: "Sign in" │
                                          └──────────────────────┘
```

**Summary:**

| Token | TTL | When It Expires | User Impact |
|-------|-----|-----------------|-------------|
| Access Token | 1 hour | Every hour | **None** — extension silently refreshes |
| Refresh Token | 30 days | After 30 days of inactivity | **Prompt** — user clicks "Sign In" (30 sec) |

**Why 30 days?** Security feature — if someone steals your laptop, tokens become useless after 30 days without your SSO password.

### How Kong Gateway Validates JWT (JWKS Flow)

**Critical Point:** Kong Gateway does NOT call PingFederate for every request. It uses cached **public keys** to validate JWT signatures locally.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JWT VALIDATION (NO PER-REQUEST CALLS)                    │
└─────────────────────────────────────────────────────────────────────────────┘

ONE-TIME SETUP (Kong startup + refresh every 5 minutes):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Kong Gateway                              PingFederate
       │                                         │
       │  GET https://sso.company.com/pf/JWKS    │
       │────────────────────────────────────────▶│
       │                                         │
       │  Response (PUBLIC KEYS):                │
       │  { "keys": [                            │
       │    { "kid": "key-123",                  │
       │      "kty": "RSA",                      │
       │      "n": "0vx7agoebGc...",             │ ← Public key modulus
       │      "e": "AQAB" }                      │
       │  ]}                                     │
       │◀────────────────────────────────────────│
       │                                         │
       └─ Cache in memory (refresh every 5 min)


PER-REQUEST VALIDATION (local crypto, NO network call):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  VS Code Request                         Kong Gateway
       │                                       │
       │  POST /kafka/dev                      │
       │  Authorization: Bearer eyJhbG...      │
       │──────────────────────────────────────▶│
       │                                       │
       │       ┌───────────────────────────────┴────────────────────────────┐
       │       │                                                            │
       │       │  1. EXTRACT: Get JWT from Authorization header             │
       │       │                                                            │
       │       │  2. DECODE HEADER: { "alg": "RS256", "kid": "key-123" }   │
       │       │                                           │                │
       │       │  3. LOOKUP: Find public key from cache ───┘                │
       │       │             cached_keys["key-123"] → RSA public key       │
       │       │                                                            │
       │       │  4. VERIFY SIGNATURE (local crypto operation):             │
       │       │     RSA_VERIFY(                                           │
       │       │       data = header + "." + payload,                      │
       │       │       signature = token.signature,                        │
       │       │       key = PUBLIC_KEY from cache                         │
       │       │     )                                                      │
       │       │     ✓ Returns TRUE → Token was signed by PingFederate    │
       │       │                                                            │
       │       │  5. CHECK EXPIRY: token.exp > now? ✓                      │
       │       │                                                            │
       │       │  6. CHECK AUDIENCE: token.aud == "mcp-api"? ✓            │
       │       │                                                            │
       │       │  All checks pass → Forward to MCP server                  │
       │       │                                                            │
       │       └────────────────────────────────────────────────────────────┘
       │                                       │
       │                                       ▼
       │                               kafka-mcp-dev-service
       │                              (NO auth code — trusts gateway)
```

**Why This Is Secure:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ASYMMETRIC CRYPTOGRAPHY (RSA)                          │
│                                                                             │
│   ┌──────────────────┐                  ┌──────────────────┐               │
│   │   PRIVATE KEY    │                  │   PUBLIC KEY     │               │
│   │                  │                  │                  │               │
│   │  • Kept SECRET   │                  │  • Published at  │               │
│   │  • Only PingFed  │                  │    /pf/JWKS     │               │
│   │    has it        │                  │  • Kong caches it│               │
│   │                  │                  │                  │               │
│   │  Used to SIGN    │       ═══▶       │  Used to VERIFY  │               │
│   │  (create tokens) │  mathematically  │  (validate sign) │               │
│   │                  │     linked       │                  │               │
│   └──────────────────┘                  └──────────────────┘               │
│                                                                             │
│   • Only PingFederate can CREATE valid tokens (has private key)            │
│   • Anyone with public key can VERIFY tokens (Kong)                        │
│   • CANNOT forge a token without the private key                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Kong JWT Plugin Configuration:**

```yaml
# Kong JWT Plugin for JWKS validation
apiVersion: configuration.konghq.com/v1
kind: KongPlugin
metadata:
  name: jwt-auth
  namespace: mcp
plugin: jwt
config:
  # Where to find the token
  header_names:
    - Authorization

  # JWKS endpoint (PingFederate's public keys)
  # Kong fetches and caches automatically
  jwks_uri: "https://sso.company.com/pf/JWKS"

  # Cache refresh interval (seconds)
  jwks_cache_timeout: 300  # 5 minutes

  # Claims to validate
  claims_to_verify:
    - exp       # Token not expired

  # Expected audience
  # audience: "mcp-api"

  # Which claim identifies the signing key
  key_claim_name: "kid"

  # Algorithm whitelist
  algorithms:
    - RS256     # RSA + SHA-256
```

**Performance:**

| Operation | Latency | Network Call? |
|-----------|---------|---------------|
| JWKS fetch (startup) | ~50ms | Yes (once) |
| JWKS refresh | ~50ms | Yes (every 5 min) |
| JWT validation (per request) | ~1ms | **No** (local crypto) |

### Why MCP Server Has Zero Auth Code

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SECURITY BOUNDARY                                   │
│                                                                             │
│   Internet ──────▶  [ Kong Gateway ]  ──────▶  [ MCP Server ]              │
│   (untrusted)           (public)               (private K8s)               │
│                            │                        │                       │
│                      VALIDATES JWT             TRUSTS GATEWAY               │
│                      (public network)          (private network)            │
│                                                                             │
│   • Kong is the ONLY entry point from internet (via ALB)                   │
│   • MCP servers are on private Kubernetes network (ClusterIP, no Ingress) │
│   • NetworkPolicy: MCP pods accept traffic ONLY from Kong namespace        │
│   • If request reaches MCP server, it ALREADY passed gateway auth          │
│   • Double-validation would be redundant and add latency                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

This is standard microservice pattern (same as how internal services work at Netflix, Uber, etc.):

| Component | Network Exposure | Auth Responsibility |
|-----------|------------------|---------------------|
| Kong Gateway | Public (ALB → Kong) | Validates JWT signature, expiry, audience |
| MCP Server | Private (K8s ClusterIP) | Zero auth code — trusts gateway |

### Credential Separation

| Who | Credential | Where Stored | Purpose |
|-----|-----------|-------------|----------|
| **Developer** | JWT token (from PingFederate) | VS Code Secret Storage (OS keychain) | Proves identity to API gateway |
| **API Gateway** | PingFederate JWKS public key | Gateway config | Validates JWT signature |
| **Kafka MCP Server** | Kafka service account credentials | K8s Secrets / AWS Secrets Manager | Connects to Kafka cluster |
| **Database MCP Server** | DB service account credentials | K8s Secrets / AWS Secrets Manager | Connects to database |
| **Redis MCP Server** | Redis credentials | K8s Secrets / AWS Secrets Manager | Connects to Redis cluster |
| **Developer never sees** | Backend credentials | N/A | Developer has zero knowledge of backend creds |

**Auth code exists in exactly 2 places:**
1. **VS Code Extension** — gets the JWT token from PingFederate
2. **API Gateway** — validates the JWT token

**MCP servers have ZERO auth code.** They receive pre-authenticated requests from the gateway.

### Security Layers

| Layer | Security Measure | Implementation |
|-------|------------------|----------------|
| **1. Network** | TLS 1.3 encryption | HTTPS (encrypted in transit) |
| **2. Authentication** | OAuth 2.0 + SSO | PingFederate integration |
| **3. Token Validation** | JWT signature verification | API Gateway validates PingFederate-signed tokens |
| **4. API Gateway** | Rate limiting + routing | Throttle per user, route to correct MCP server |
| **5. Backend Access** | Pod-level credentials | Each MCP server has its own backend credentials |
| **6. Audit** | All operations logged | Every MCP request logged with user identity |
| **7. Secrets** | AWS Secrets Manager | Kafka creds, PingFederate client secrets |

### mcp.json Configuration

> **Note:** `mcp.json` contains **only server URLs** — no tokens. Authentication is handled by the MCP Auth extension.

**Complete mcp.json Reference (Multi-Environment, Multi-Service):**

```json
{
  "servers": {
    
    // ═══════════════════════════════════════════════════════════════════════
    // KAFKA MCP SERVERS (4 environments)
    // ═══════════════════════════════════════════════════════════════════════
    
    "kafka-dev": {
      "type": "http",
      "url": "https://mcp.company.com/kafka/dev",
      "label": "Kafka DEV (us-east-1)"
    },
    "kafka-sit": {
      "type": "http",
      "url": "https://mcp.company.com/kafka/sit",
      "label": "Kafka SIT (us-east-1)"
    },
    "kafka-uat": {
      "type": "http",
      "url": "https://mcp.company.com/kafka/uat",
      "label": "Kafka UAT (us-east-1)"
    },
    "kafka-prod": {
      "type": "http",
      "url": "https://mcp.company.com/kafka/prod",
      "label": "Kafka PROD (us-east-1)"
    },
    
    // ═══════════════════════════════════════════════════════════════════════
    // DATABASE MCP SERVERS (4 environments)
    // ═══════════════════════════════════════════════════════════════════════
    
    "database-dev": {
      "type": "http",
      "url": "https://mcp.company.com/database/dev",
      "label": "PostgreSQL DEV (us-east-1)"
    },
    "database-sit": {
      "type": "http",
      "url": "https://mcp.company.com/database/sit",
      "label": "PostgreSQL SIT (us-east-1)"
    },
    "database-uat": {
      "type": "http",
      "url": "https://mcp.company.com/database/uat",
      "label": "PostgreSQL UAT (us-east-1)"
    },
    "database-prod": {
      "type": "http",
      "url": "https://mcp.company.com/database/prod",
      "label": "PostgreSQL PROD (us-east-1)"
    }
  }
}
```

**Minimal mcp.json (dev only):**

```json
{
  "servers": {
    "kafka-dev": {
      "type": "http",
      "url": "https://mcp.company.com/kafka/dev"
    },
    "database-dev": {
      "type": "http",
      "url": "https://mcp.company.com/database/dev"
    }
  }
}
```

**URL Pattern Breakdown:**

```
https://mcp.company.com/kafka/dev
│       │              │     │
│       │              │     └── Environment: dev, sit, uat, prod
│       │              └──────── Service: kafka, database, s3, etc.
│       └─────────────────────── Gateway domain (single entry point)
└─────────────────────────────── HTTPS (TLS required)
```

**Gateway Route Mapping:**

| mcp.json URL Path | Routes To (K8s Service) |
|-------------------|-------------------------|
| `/kafka/dev` | `kafka-mcp-dev-service:8000` |
| `/kafka/sit` | `kafka-mcp-sit-service:8000` |
| `/kafka/uat` | `kafka-mcp-uat-service:8000` |
| `/kafka/prod` | `kafka-mcp-prod-service:8000` |
| `/database/dev` | `database-mcp-dev-service:8000` |
| `/database/sit` | `database-mcp-sit-service:8000` |
| `/database/uat` | `database-mcp-uat-service:8000` |
| `/database/prod` | `database-mcp-prod-service:8000` |

**Example Copilot Interactions:**

| Developer Says | Copilot Matches | MCP Server Called |
|----------------|-----------------|-------------------|
| "List all topics from dev" | kafka-dev | `https://mcp.company.com/kafka/dev` |
| "Show tables in UAT database" | database-uat | `https://mcp.company.com/database/uat` |
| "Create topic orders on prod" | kafka-prod | `https://mcp.company.com/kafka/prod` |
| "Describe users table in SIT" | database-sit | `https://mcp.company.com/database/sit` |
| "List topics" (no env) | Asks: "Which environment?" | — |

**Recommended Location:**

| Location | Recommendation |
|----------|----------------|
| `~/.vscode/mcp.json` (user-level) | ✅ **Recommended** — applies to all workspaces |
| `.vscode/mcp.json` (workspace) | ✅ OK — can be committed (no secrets) |

**How Authentication Works:**

1. Extension installed → prompts sign-in → tokens stored (keychain + memory)
2. Every request: `getSession()` returns from memory → header attached → request sent

### Rate Limiting Strategy (Per User)

Rate limits are enforced at the API Gateway layer, identified by the `sub` claim in the JWT token.

**Request Rate Limits**

| Cluster | Requests/min | Requests/hr | Rationale |
|---------|-------------|-------------|-----------|
| Dev | 120 | 2,000 | Higher — developers experiment more |
| Staging | 60 | 1,000 | Moderate usage |
| Prod | 30 | 500 | Stricter — protect production |

**Per-Tool Limits (by operation type)**

| Operation Type | Tools | Limit | Rationale |
|---|---|---|---|
| Read-only | `list_topics`, `describe_topic`, `topic_exists`, `cluster_overview` | Standard (above) | Safe, no side effects |
| Mutating | `create_topic`, `update_topic` | 5/min | Creates/modifies resources |
| Destructive | `delete_topic` | 2/min | Irreversible — hard safety cap |

**Concurrent Connection Limits**

| Limit | Value | Rationale |
|---|---|---|
| Concurrent requests per user | 10 | Reasonable for interactive use |
| Concurrent requests total (gateway) | 10,000 | 1,000 users × 10 concurrent |

**Role-Based Overrides**

| Role | Multiplier | Example (prod) |
|---|---|---|
| `developer` | 1× (standard) | 30 req/min |
| `kafka-admin` | 2× | 60 req/min |
| `service-account` | 5× | 150 req/min |

**Rate Limit Behavior:**
- Storage: Redis (distributed counting across gateway pods)
- On Redis failure: **allow requests** (fault-tolerant — don't block all users)
- Response when exceeded: HTTP 429 with `Retry-After` header
- Headers returned: `X-RateLimit-Limit`, `X-RateLimit-Remaining`

---

## 🏗️ 2. Infrastructure Components (Kubernetes on AWS)

### Kubernetes Architecture (EKS)

```yaml
# =========================================
# NAMESPACE STRUCTURE
# =========================================
Namespaces:
  - mcp-system       # Platform services
  - mcp-kafka-dev    # Dev Kafka MCP pods
  - mcp-kafka-staging
  - mcp-kafka-prod   # Production (HA, strict RBAC)
  
# =========================================
# DEPLOYMENT: MCP Routing Service
# =========================================
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mcp-routing-service
  namespace: mcp-system
spec:
  replicas: 3
  selector:
    matchLabels:
      app: mcp-routing
  template:
    metadata:
      labels:
        app: mcp-routing
    spec:
      serviceAccountName: mcp-routing-sa
      containers:
      - name: mcp-routing
        image: company-ecr.amazonaws.com/mcp-routing:v1.2.3
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: KAFKA_CLUSTERS_CONFIG
          valueFrom:
            configMapKeyRef:
              name: kafka-clusters
              key: clusters.yaml
        - name: OAUTH_ISSUER
          value: "https://auth.company.com"
        - name: REDIS_URL
          valueFrom:
            secretKeyRef:
              name: redis-creds
              key: url
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5

---
# =========================================
# DEPLOYMENT: Kafka MCP Server (Per Cluster)
# =========================================
# MCP servers are STATELESS — use Deployment (not StatefulSet)
# Benefits of Deployment over StatefulSet:
#   - Faster scaling (parallel pod creation)
#   - Faster rollouts (all pods update at once)
#   - Simpler — no ordering constraints
# StatefulSet is for stateful apps (databases), MCP servers have no local state.

apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka-mcp-dev-us-east-1
  namespace: mcp-kafka-dev
spec:
  replicas: 2  # HA for reliability
  selector:
    matchLabels:
      app: kafka-mcp
      cluster: dev-us-east-1
  template:
    metadata:
      labels:
        app: kafka-mcp
        cluster: dev-us-east-1
    spec:
      serviceAccountName: kafka-mcp-dev-sa
      # =========================================
      # GRACEFUL SHUTDOWN
      # =========================================
      terminationGracePeriodSeconds: 60  # Allow 60s for in-flight requests
      containers:
      - name: kafka-mcp
        image: company-ecr.amazonaws.com/kafka-mcp-python:v2.1.0
        ports:
        - containerPort: 8000
          name: mcp-api
        # =========================================
        # LIFECYCLE HOOKS: Graceful shutdown
        # =========================================
        lifecycle:
          preStop:
            exec:
              # Wait 30s before SIGTERM to allow:
              # 1. Load balancer to stop sending new requests
              # 2. In-flight requests to complete
              command: ["/bin/sh", "-c", "sleep 30"]
        env:
        - name: CLUSTER_ID
          value: "dev-us-east-1"
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            secretKeyRef:
              name: msk-dev-us-east-1
              key: bootstrap-servers
        - name: AWS_REGION
          value: "us-east-1"
        - name: MSK_IAM_AUTH
          value: "true"
        - name: LOG_LEVEL
          value: "INFO"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        # =========================================
        # HEALTH CHECKS (Added for completeness)
        # =========================================
        livenessProbe:
          httpGet:
            path: /health
            port: 8000
          initialDelaySeconds: 10
          periodSeconds: 15
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /ready
            port: 8000
          initialDelaySeconds: 5
          periodSeconds: 5
          failureThreshold: 2
        volumeMounts:
        - name: config
          mountPath: /app/config
      volumes:
      - name: config
        configMap:
          name: kafka-mcp-config

---
# =========================================
# HPA: Auto-scaling based on CPU/Requests
# =========================================
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: kafka-mcp-dev-hpa
  namespace: mcp-kafka-dev
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kafka-mcp-dev-us-east-1
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: mcp_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"

---
# =========================================
# SERVICE: Internal Load Balancer
# =========================================
apiVersion: v1
kind: Service
metadata:
  name: kafka-mcp-dev-service
  namespace: mcp-kafka-dev
spec:
  type: ClusterIP
  selector:
    app: kafka-mcp
    cluster: dev-us-east-1
  ports:
  - port: 8000
    targetPort: 8000
    name: mcp-api

---
# =========================================
# INGRESS: Kong Gateway Route
# =========================================
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mcp-kafka-ingress
  namespace: mcp-system
  annotations:
    konghq.com/plugins: oauth-validator,rate-limiter,request-logger
    konghq.com/strip-path: "true"
spec:
  ingressClassName: kong
  rules:
  - host: mcp.company.com
    http:
      paths:
      - path: /kafka/dev
        pathType: Prefix
        backend:
          service:
            name: kafka-mcp-dev-service
            port:
              number: 8000
      - path: /kafka/staging
        pathType: Prefix
        backend:
          service:
            name: kafka-mcp-staging-service
            port:
              number: 8000
      - path: /kafka/prod
        pathType: Prefix
        backend:
          service:
            name: kafka-mcp-prod-service
            port:
              number: 8000

---
# =========================================
# KONG PLUGINS: Circuit Breaker, Rate Limiting
# =========================================
apiVersion: configuration.konghq.com/v1
kind: KongPlugin
metadata:
  name: circuit-breaker
  namespace: mcp-system
config:
  # Circuit breaker configuration
  failure_count_threshold: 5          # Open circuit after 5 failures
  failure_window_seconds: 30          # Within 30 seconds
  open_duration_seconds: 60           # Stay open for 60 seconds
  half_open_requests: 3               # Allow 3 test requests in half-open state
plugin: request-termination           # Or use custom circuit breaker plugin

---
# =========================================
# KONG PLUGIN: OAuth JWT Validation
# =========================================
apiVersion: configuration.konghq.com/v1
kind: KongPlugin
metadata:
  name: oauth-validator
  namespace: mcp-system
config:
  # JWKS-based JWT validation (no per-request PingFederate calls)
  key_claim_name: kid
  claims_to_verify:
    - exp                              # Token not expired
    - aud                              # Audience is "mcp-api"
  jwks_uri: "https://sso.company.com/pf/JWKS"
  jwks_cache_ttl: 3600                 # Cache JWKS for 1 hour
  audience: "mcp-api"
  issuer: "https://sso.company.com/pingfederate"
plugin: jwt

---
# =========================================
# POD DISRUPTION BUDGET (Critical for HA)
# =========================================
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: kafka-mcp-dev-pdb
  namespace: mcp-kafka-dev
spec:
  minAvailable: 1                      # At least 1 pod during node drains
  selector:
    matchLabels:
      app: kafka-mcp
      cluster: dev-us-east-1
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: kafka-mcp-staging-pdb
  namespace: mcp-kafka-staging
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: kafka-mcp
      cluster: staging-us-east-1
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: kafka-mcp-prod-pdb
  namespace: mcp-kafka-prod
spec:
  minAvailable: 2                      # Prod requires 2 pods minimum
  selector:
    matchLabels:
      app: kafka-mcp
      cluster: prod-us-east-1

---
# =========================================
# NETWORK POLICY: Isolate MCP Pods  
# =========================================
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: kafka-mcp-network-policy
  namespace: mcp-kafka-dev
spec:
  podSelector:
    matchLabels:
      app: kafka-mcp
  policyTypes:
  - Ingress
  - Egress
  ingress:
  # Only allow traffic from Kong gateway (mcp-system namespace)
  - from:
    - namespaceSelector:
        matchLabels:
          name: mcp-system
    ports:
    - protocol: TCP
      port: 8000
  egress:
  # Allow traffic to MSK clusters (VPC CIDR)
  - to:
    - ipBlock:
        cidr: 10.0.0.0/8              # VPC CIDR - adjust to your VPC
    ports:
    - protocol: TCP
      port: 9098                       # MSK IAM port
  # Allow DNS resolution
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: UDP
      port: 53
  # Allow traffic to AWS APIs (STS for IRSA)
  - to:
    - ipBlock:
        cidr: 0.0.0.0/0
    ports:
    - protocol: TCP
      port: 443
```

### IAM Roles for MSK Access (Pod Identity)

```yaml
# =========================================
# EKS Pod IAM Role (IRSA - IAM Roles for Service Accounts)
# =========================================

# 1. Create IAM Role for Dev Kafka MCP
resource "aws_iam_role" "kafka_mcp_dev" {
  name = "kafka-mcp-dev-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = "arn:aws:iam::${var.account_id}:oidc-provider/${var.oidc_provider}"
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${var.oidc_provider}:sub" = "system:serviceaccount:mcp-kafka-dev:kafka-mcp-dev-sa"
        }
      }
    }]
  })
}

# 2. Attach MSK IAM Policy
resource "aws_iam_role_policy" "kafka_mcp_dev_msk" {
  name = "msk-access"
  role = aws_iam_role.kafka_mcp_dev.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:DescribeCluster",
          "kafka-cluster:DescribeClusterDynamicConfiguration",
          "kafka-cluster:AlterCluster",
          "kafka-cluster:AlterClusterDynamicConfiguration",
          "kafka-cluster:DescribeTopic",
          "kafka-cluster:CreateTopic",
          "kafka-cluster:DeleteTopic",
          "kafka-cluster:AlterTopic",
          "kafka-cluster:ReadData",
          "kafka-cluster:WriteData",
          "kafka-cluster:DescribeGroup",
          "kafka-cluster:AlterGroup",
          "kafka-cluster:DeleteGroup"
        ]
        Resource = [
          "arn:aws:kafka:us-east-1:${var.account_id}:cluster/dev-kafka/*",
          "arn:aws:kafka:us-east-1:${var.account_id}:topic/dev-kafka/*/*",
          "arn:aws:kafka:us-east-1:${var.account_id}:group/dev-kafka/*/*"
        ]
      }
    ]
  })
}

# 3. Kubernetes Service Account with IAM annotation
apiVersion: v1
kind: ServiceAccount
metadata:
  name: kafka-mcp-dev-sa
  namespace: mcp-kafka-dev
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::${ACCOUNT_ID}:role/kafka-mcp-dev-role
```

---

## 🔀 3. Multi-Cluster Kafka Management

### Cluster Configuration (ConfigMap)

```yaml
# =========================================
# CONFIGMAP: Kafka Clusters Registry
# =========================================
apiVersion: v1
kind: ConfigMap
metadata:
  name: kafka-clusters
  namespace: mcp-system
data:
  clusters.yaml: |
    clusters:
      # Development Clusters
      - id: dev-us-east-1
        name: "Development (US East)"
        environment: development
        region: us-east-1
        bootstrap_servers: "b-1.dev-kafka.xxxxx.c2.kafka.us-east-1.amazonaws.com:9098"
        auth_method: IAM
        mcp_namespace: mcp-kafka-dev
        allowed_operations:
          - list_topics
          - describe_topic
          - create_topic
          - delete_topic
          - cluster_overview
        rate_limit: 100  # requests per minute per user
        
      # Staging Clusters
      - id: staging-us-east-1
        name: "Staging (US East)"
        environment: staging
        region: us-east-1
        bootstrap_servers: "b-1.staging-kafka.xxxxx.c2.kafka.us-east-1.amazonaws.com:9098"
        auth_method: IAM
        mcp_namespace: mcp-kafka-staging
        allowed_operations:
          - list_topics
          - describe_topic
          - create_topic
          - delete_topic
          - update_topic
          - cluster_overview
        rate_limit: 50
        
      # Production Clusters (Multiple Regions)
      - id: prod-us-east-1
        name: "Production (US East)"
        environment: production
        region: us-east-1
        bootstrap_servers: "b-1.prod-kafka.xxxxx.c2.kafka.us-east-1.amazonaws.com:9098"
        auth_method: IAM
        mcp_namespace: mcp-kafka-prod
        allowed_operations:
          - list_topics
          - describe_topic
          - cluster_overview
          # No create/delete for most users!
        admin_operations:  # Requires kafka-admin role
          - create_topic
          - delete_topic
          - update_topic
        rate_limit: 30
        require_approval_for:
          - delete_topic
          - update_topic
          
      - id: prod-eu-west-1
        name: "Production (EU West)"
        environment: production
        region: eu-west-1
        bootstrap_servers: "b-1.prod-kafka-eu.xxxxx.c2.kafka.eu-west-1.amazonaws.com:9098"
        auth_method: IAM
        mcp_namespace: mcp-kafka-prod
        allowed_operations:
          - list_topics
          - describe_topic
          - cluster_overview
        admin_operations:
          - create_topic
          - delete_topic
        rate_limit: 30
        gdpr_compliant: true  # Special handling
        
      - id: analytics-us-west-2
        name: "Analytics (US West)"
        environment: analytics
        region: us-west-2
        bootstrap_servers: "b-1.analytics-kafka.xxxxx.c2.kafka.us-west-2.amazonaws.com:9098"
        auth_method: IAM
        mcp_namespace: mcp-kafka-analytics
        allowed_operations:
          - list_topics
          - describe_topic
          - cluster_overview
        teams_allowed:  # Only analytics team
          - data-analytics
          - data-science
        rate_limit: 20
```

### Routing Logic (Python FastAPI)

```python
# mcp-routing-service/app/routing.py

from fastapi import FastAPI, Header, HTTPException, Depends
from pydantic import BaseModel
import jwt
import yaml
import httpx

app = FastAPI()

# Load cluster config
with open('/config/clusters.yaml') as f:
    CLUSTERS = yaml.safe_load(f)['clusters']

def verify_jwt(authorization: str = Header(...)) -> dict:
    """Verify JWT token and extract user info"""
    try:
        token = authorization.replace('Bearer ', '')
        payload = jwt.decode(
            token, 
            OAUTH_PUBLIC_KEY, 
            algorithms=['RS256'],
            audience='mcp-api'
        )
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")

def get_allowed_clusters(user_info: dict) -> list:
    """Determine which clusters user can access"""
    allowed = []
    
    for cluster in CLUSTERS:
        # Check environment-based access
        if cluster['environment'] == 'development':
            allowed.append(cluster)  # All users can access dev
        
        elif cluster['environment'] == 'staging':
            if 'developer' in user_info['roles']:
                allowed.append(cluster)
        
        elif cluster['environment'] == 'production':
            # Check if user's team is allowed
            if 'teams_allowed' in cluster:
                if any(team in user_info['teams'] for team in cluster['teams_allowed']):
                    allowed.append(cluster)
            # Or check explicit cluster list in JWT
            elif cluster['id'] in user_info.get('allowed_clusters', []):
                allowed.append(cluster)
        
        elif cluster['environment'] == 'analytics':
            if any(team in ['data-analytics', 'data-science'] for team in user_info['teams']):
                allowed.append(cluster)
    
    return allowed

@app.get("/clusters")
async def list_clusters(user_info: dict = Depends(verify_jwt)):
    """List clusters user can access"""
    allowed = get_allowed_clusters(user_info)
    return {
        "clusters": [
            {
                "id": c['id'],
                "name": c['name'],
                "environment": c['environment'],
                "region": c['region']
            }
            for c in allowed
        ]
    }

@app.post("/kafka/{cluster_id}/tools/call")
async def call_tool(
    cluster_id: str,
    request: dict,
    user_info: dict = Depends(verify_jwt)
):
    """Route MCP tool call to appropriate Kafka MCP pod"""
    
    # 1. Validate cluster access
    allowed_clusters = get_allowed_clusters(user_info)
    cluster = next((c for c in allowed_clusters if c['id'] == cluster_id), None)
    
    if not cluster:
        raise HTTPException(status_code=403, detail=f"Access denied to cluster {cluster_id}")
    
    # 2. Validate operation permission
    tool_name = request.get('name')
    
    # Check if operation is allowed for environment
    if tool_name not in cluster['allowed_operations']:
        # Check if it's an admin operation
        if tool_name in cluster.get('admin_operations', []):
            if 'kafka-admin' not in user_info['roles']:
                raise HTTPException(
                    status_code=403, 
                    detail=f"Operation {tool_name} requires kafka-admin role"
                )
        else:
            raise HTTPException(
                status_code=403,
                detail=f"Operation {tool_name} not allowed on {cluster['environment']}"
            )
    
    # 3. Check if approval required
    if tool_name in cluster.get('require_approval_for', []):
        if not request.get('approval_ticket'):
            raise HTTPException(
                status_code=403,
                detail=f"Operation {tool_name} requires approval ticket (JIRA/ServiceNow)"
            )
        # Validate approval ticket with external system
        await validate_approval_ticket(request['approval_ticket'], user_info['email'])
    
    # 4. Apply rate limiting (check Redis)
    rate_limit_key = f"rate:{user_info['user_id']}:{cluster_id}"
    if not await check_rate_limit(rate_limit_key, cluster['rate_limit']):
        raise HTTPException(status_code=429, detail="Rate limit exceeded")
    
    # 5. Route to backend MCP pod (with retry and circuit breaker)
    backend_url = f"http://kafka-mcp-{cluster_id}-service.{cluster['mcp_namespace']}.svc.cluster.local:8000"
    
    # Generate request ID for tracing
    import uuid
    request_id = str(uuid.uuid4())[:8]
    
    async with httpx.AsyncClient() as client:
        # Retry configuration
        max_retries = 3
        retry_delays = [0.5, 1.5, 4.5]  # Exponential backoff
        last_error = None
        
        for attempt in range(max_retries):
            try:
                response = await client.post(
                    f"{backend_url}/tools/call",
                    json=request,
                    headers={
                        "X-User-Id": user_info['user_id'],
                        "X-User-Email": user_info['email'],
                        "X-Cluster-Id": cluster_id,
                        "X-Request-Id": request_id  # For distributed tracing
                    },
                    timeout=30.0
                )
                
                # 6. Audit log
                await log_operation(
                    user_id=user_info['user_id'],
                    cluster_id=cluster_id,
                    operation=tool_name,
                    params=request.get('arguments', {}),
                    success=response.status_code == 200,
                    request_id=request_id
                )
                
                if response.status_code >= 500:
                    # Server error - retry
                    last_error = f"Backend returned {response.status_code}"
                    if attempt < max_retries - 1:
                        await asyncio.sleep(retry_delays[attempt])
                        continue
                
                return response.json()
                
            except httpx.TimeoutException:
                last_error = "Request timed out"
                if attempt < max_retries - 1:
                    await asyncio.sleep(retry_delays[attempt])
                    continue
                    
            except httpx.ConnectError:
                last_error = "Could not connect to backend"
                if attempt < max_retries - 1:
                    await asyncio.sleep(retry_delays[attempt])
                    continue
                    
            except Exception as e:
                # Log full error internally but don't expose to client
                logger.error(f"Backend error [req={request_id}]: {type(e).__name__}: {e}")
                last_error = "Internal error"
                break
        
        # All retries exhausted
        raise HTTPException(
            status_code=502, 
            detail={
                "error": "Backend unavailable",
                "message": f"Could not complete request after {max_retries} attempts. {last_error}",
                "request_id": request_id,
                "action": "Please retry in a few seconds. If the problem persists, contact #mcp-support"
            }
        )
```

---

## 👥 Persona-Based Views

### 🎨 Architect Concerns

**Questions & Answers:**

**Q1: How do we ensure high availability (HA) in a single region?**
- Multi-AZ EKS cluster (3 availability zones)
- Deployment replicas=2 minimum per cluster (Deployment, not StatefulSet — MCP servers are stateless)
- ALB health checks with automatic pod replacement
- RTO: <5 minutes, RPO: 0 (no data loss, Kafka is source of truth)
- Backup strategy: Configuration in Git, audit logs in S3

**DR status:** Multi-region disaster recovery is **out of scope** for the current phase (TBD for future). No cross-region failover is implemented.

**Q2: How do we handle burst traffic (Black Friday scenario)?**
- HPA: auto-scale from 2→10 replicas based on CPU/requests
- Kong rate limiting protects backend (100 req/min per user)
- Redis caching for list_topics (30s TTL)
- Circuit breaker prevents cascade failures

**Q3: What's the blast radius if MCP server compromised?**
- Each cluster = separate namespace + IAM role (isolation)
- Principle of least privilege (dev can't access prod)
- Network policies: MCP pods can ONLY talk to assigned MSK cluster
- Audit logs → SIEM for anomaly detection
- Automatic pod termination if suspicious activity detected

**Q4: Cost optimization strategy?**
- Right-size pods (512MB is enough for 100 req/min)
- Use Spot instances for dev/staging (50% cost savings)
- Reserved instances for prod MCP pods (40% savings)
- Auto-scale down during nights/weekends (save 30%)

**Q5: How to handle regulatory compliance (SOC2, GDPR)?**
- All operations logged with full audit trail
- GDPR flag on EU clusters (data residency enforcement)
- Encryption at rest (EBS volumes encrypted)
- Encryption in transit (TLS 1.3)
- Data retention policies enforced (90 days)
- Right to be forgotten (delete user audit logs)

**Q6: Why Kong Gateway instead of AWS API Gateway?**
- **Latency:** Kong runs in same VPC as MCP servers (~1-5ms). AWS API Gateway adds ~20-50ms via VPC Link.
- **Cost:** Kong is fixed cost (pods). AWS API Gateway charges per request ($3.50/million).
- **Control:** Full control over Kong plugins, routing, rate limiting.
- **Kubernetes-native:** Kong integrates with Ingress, CRDs. AWS API Gateway is separate infrastructure.
- See ADR-003 for detailed comparison.

**Q7: Why Streamable HTTP instead of SSE (Server-Sent Events)?**
- **Kafka operations are fast:** `list_topics`, `describe_topic` complete in <500ms. No need for streaming.
- **Simpler architecture:** Stateless HTTP requests, no persistent connections to manage.
- **Better scaling:** No connection pooling issues at gateway.
- **Standard HTTP:** Works with all proxies, load balancers, firewalls.
- See ADR-004 for detailed comparison.

**Q8: How does the gateway validate JWT without calling PingFederate per request?**
- Kong fetches **JWKS (public keys)** from PingFederate at startup, caches for 5 minutes.
- Per request: Kong validates JWT signature using cached public key (local crypto, ~1ms).
- **No network call per request.** This is standard JWT validation pattern.
- If PingFederate is down: Existing tokens continue to work for up to 1 hour (JWKS cache TTL).
- See "How Kong Gateway Validates JWT (JWKS Flow)" section for visual diagram.

**Q9: How does the VS Code extension know which MCP server to call?**
- Extension doesn't decide — **Copilot does.**
- Copilot matches user intent ("list topics from dev") to MCP server name (`kafka-dev`).
- mcp.json maps server names to URLs: `kafka-dev` → `https://mcp.company.com/kafka/dev`
- Same JWT token works for all MCP servers (token represents user, not server).
- See "End-to-End Request Flow" section for complete walkthrough.

---

### 💻 Developer Concerns

**Questions & Answers:**

**Q1: How do I get started? (Onboarding)**

```
Step 1: Install MCP Auth Extension (2 minutes)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  • Open VS Code Extensions (Ctrl+Shift+X / Cmd+Shift+X)
  • Search: "Company MCP Auth"
  • Click Install
  • Extension prompts: "Sign in to Company SSO"
  • Browser opens → SSO login → Done!

Step 2: Add mcp.json (1 minute, one-time)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  • Create file: ~/.vscode/mcp.json (or get from team wiki)
  • Paste this:
  
  {
    "servers": {
      "kafka-dev": {
        "type": "http",
        "url": "https://mcp.company.com/kafka/dev"
      },
      "kafka-prod": {
        "type": "http",
        "url": "https://mcp.company.com/kafka/prod"
      }
    }
  }

Step 3: Start using! (0 minutes)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  • Open Copilot Chat (Ctrl+Shift+I / Cmd+Shift+I)
  • Type: "List all topics from dev"
  • Copilot calls the MCP server, shows results!

That's it. No CLI tools, no environment variables, no local servers.
```

**Q2: How does Copilot know which MCP server to use?**
- When you say "list topics from **dev**", Copilot matches "dev" to the `kafka-dev` server in mcp.json
- When you say "show tables in **UAT database**", it matches to `database-uat`
- If ambiguous (e.g., just "list topics"), Copilot asks: "Which environment?"
- The matching is based on server names and labels in mcp.json

**Q3: Which clusters can I access?**
- You can access any cluster whose URL is in your mcp.json
- Your JWT token (from SSO) determines permissions at the gateway
- Typically: All devs get dev + staging access. Prod requires approval.
- To check your access: Try "list topics on prod" — if 403, you don't have access

**Q4: I need production access, how?**
- Submit ticket in self-service portal: https://mcp-portal.company.com/request-access
- Select cluster(s) you need
- Justify business need
- Manager approval required (automatic email)
- Access granted in 1 business day
- Time-limited (review every 90 days)

**Q5: I'm getting rate limited, why?**
- Dev: 120 req/min per user (very generous)
- Staging: 60 req/min
- Prod: 30 req/min (protection against accidents)
- If you hit limit: Wait 1 minute, then retry
- If you need more: Create ticket explaining use case

**Q6: Can I create topics in production?**
- Regular developers: Read-only access (list, describe)
- Kafka admins: Can create/delete with approval ticket
- Destructive operations require JIRA/ServiceNow ticket
- Approval workflow: Submit ticket → Manager approves → Operation allowed

**Q7: My token seems expired, what do I do?**
- **Normally:** Extension auto-refreshes silently (every hour). You won't notice.
- **After 30 days inactive:** You'll see "Your session has expired. Sign in again."
  - Click "Sign In" → Browser → SSO → 30 seconds → Done
- **If still issues:** 
  1. Open VS Code Command Palette (Ctrl+Shift+P / Cmd+Shift+P)
  2. Type: "Sign Out of Company SSO"
  3. Then: "Sign In to Company SSO"

**Q8: What if I'm offline (airplane, no VPN)?**
- MCP requires network connectivity to the MCP servers
- Offline = no Kafka operations possible (by design — no local cache)
- VS Code will show: "Cannot connect to MCP server. Check your network connection."
- Once back online, existing authentication should still work (unless you were inactive >30 days)

**Q9: I'm behind a corporate proxy, what do I need to configure?**
- VS Code respects system proxy settings (`http_proxy`, `https_proxy` env vars)
- Extension also reads VS Code proxy settings (`http.proxy`, `http.proxyStrictSSL`)
- If issues, check with IT for proxy exceptions for `mcp.company.com` and `sso.company.com`
- Self-test: `curl -v https://mcp.company.com/health` from terminal

**Q10: How do I know how many requests I have left before rate limit?**
- Every response includes headers: `X-RateLimit-Remaining`, `X-RateLimit-Limit`
- VS Code extension status bar shows: "MCP: ✅ Connected (47/60 req/min)"
- If rate limited (HTTP 429): message includes `Retry-After` header
- Tip: Batch your queries instead of one-by-one

**Q11: I got an error. What does it mean?**

| Error | Meaning | Action |
|-------|---------|--------|
| `401 Unauthorized` | Token expired | Extension should auto-refresh. If persists (>30 days inactive), sign in again via VS Code |
| `403 Forbidden` | You don't have access to this cluster/operation | Request access via self-service portal |
| `429 Too Many Requests` | Rate limited | Wait for `Retry-After` seconds, then retry |
| `502 Backend unavailable` | MCP server pod is down or restarting | Wait 30 seconds, retry. If persists, check `#mcp-support` |
| `504 Gateway Timeout` | Operation took too long | Retry. If persists, the cluster may be overloaded |

**Q12: Do I need to configure anything in mcp.json for authentication?**
- **No!** mcp.json contains only server URLs. Example:
  ```json
  { "type": "http", "url": "https://mcp.company.com/kafka/dev" }
  ```
- The MCP Auth extension handles all authentication automatically
- Token is added to every request by VS Code's MCP client
- No tokens, secrets, or credentials in mcp.json

---

### ⚙️ Kafka Administrator Concerns

**Questions & Answers:**

**Q1: How do I add a new Kafka cluster?**
```yaml
# 1. Update ConfigMap
$ kubectl edit configmap kafka-clusters -n mcp-system

# 2. Add cluster entry (copy template from existing)
# Set: id, name, environment, bootstrap_servers, allowed_operations

# 3. Deploy new MCP Deployment
$ kubectl apply -f kafka-mcp-new-cluster.yaml

# 4. Create IAM role for pod (Terraform)
$ terraform apply -target=aws_iam_role.kafka_mcp_new_cluster

# 5. Users see it immediately in VS Code! No client-side changes needed.
```

**Q2: How do I restrict prod operations?**
- Edit cluster config in ConfigMap
- Set `admin_operations` list (create_topic, delete_topic, etc.)
- Only users with `kafka-admin` role can execute
- Optionally set `require_approval_for` to mandate tickets
- Changes propagate within 30 seconds (no restart needed)

**Q3: How do I monitor MCP usage?**
- Grafana dashboard: https://grafana.company.com/d/mcp-kafka
- Metrics:
  - Requests per second per cluster
  - Error rate by operation
  - Top users by request count
  - Latency percentiles (p50, p95, p99)
  - Rate limit violations
- Alerts configured for:
  - Error rate >5% (Slack #platform-alerts)
  - Latency p99 >1s (PagerDuty)
  - Pod crash loops (PagerDuty)

**Q4: How do I audit who created/deleted topics?**
```sql
-- PostgreSQL audit table query
SELECT 
  user_email,
  cluster_id,
  operation,
  params->>'topic_name' as topic_name,
  params->>'partitions' as partitions,
  timestamp,
  success,
  error_message
FROM mcp_audit_log
WHERE operation IN ('create_topic', 'delete_topic')
  AND cluster_id = 'prod-us-east-1'
  AND timestamp > NOW() - INTERVAL '7 days'
ORDER BY timestamp DESC;

-- Export to CSV for compliance
COPY (SELECT * FROM mcp_audit_log WHERE ...) 
TO '/tmp/audit_report.csv' CSV HEADER;
```

**Q5: Can I revoke someone's access immediately?**
Yes! Two methods:
1. **Via PingFederate** (5 minute propagation):
   - Remove user from team or revoke role in corporate directory
   - Next JWT refresh will not have access
   - User gets 403 Forbidden on next request

2. **Emergency blocklist** (5 second propagation):
   ```bash
   # Add user to Redis blocklist
   $ redis-cli SADD blocked_users "user@company.com"
   
   # Verify
   $ redis-cli SISMEMBER blocked_users "user@company.com"
   # Returns: 1 (blocked)
   
   # Remove from blocklist later
   $ redis-cli SREM blocked_users "user@company.com"
   ```

**Q6: How do I handle MSK upgrades/maintenance?**
- MSK maintenance is transparent to MCP servers
- MCP pods automatically reconnect on Kafka restart
- No downtime for users
- If issues: Check pod logs `kubectl logs -f -l app=kafka-mcp,cluster=prod-us-east-1`
- Restart pods if needed: `kubectl rollout restart deployment kafka-mcp-prod-us-east-1`

---

### 🏢 Platform/SRE Operations Concerns

**Questions & Answers:**

**Q1: How do I know when a developer leaves the company?**
- **Primary:** AD/LDAP group sync to PingFederate (automatic, ~15 min delay)
- **Secondary:** HR termination webhook → triggers emergency blocklist
- **Verification:** Weekly reconciliation script compares active users vs AD
- **Gap:** If HR doesn't process termination, stale access can persist. Automate this.

**Q2: How do I generate compliance reports?**
```bash
# Monthly access report (who has access to what)
$ mcp-admin report access --month 2026-02 --output csv

# Operations audit report (who did what)
$ mcp-admin report audit --cluster prod-us-east-1 --days 30

# Failed operations report (security investigation)
$ mcp-admin report failures --days 7
```

**Q3: How do I perform a rollback if the new extension version has bugs?**
1. **Extension rollback:**
   - VS Code Marketplace allows previous version install
   - Distribute `.vsix` of known-good version via Slack
   - Users: Extensions → MCP Auth → Install Another Version... → select previous
2. **Server rollback:**
   ```bash
   # Roll back Kubernetes deployment to previous revision
   $ kubectl rollout undo deployment/kafka-mcp-prod-us-east-1 -n mcp-kafka-prod
   
   # Or specify exact revision
   $ kubectl rollout undo deployment/kafka-mcp-prod-us-east-1 --to-revision=3
   ```

**Q4: What happens if PingFederate is down?**
- **Impact:** New sign-ins fail. Token refresh fails.
- **Mitigation:**
  - Existing valid tokens continue to work (JWT validation is local via cached JWKS)
  - JWKS cache TTL: 1 hour (can validate tokens for 1 hour after PF down)
  - Alert: "PingFederate token endpoint latency > 2s" → investigate
- **Recovery:** Once PF is back, users with expired tokens see "Sign in" prompt

**Q5: How do I handle EKS cluster upgrades?**
- **Before upgrade:**
  1. Test in staging with same K8s version
  2. Announce maintenance window (low-usage period)
  3. Verify PDBs are in place (won't evict all pods at once)
- **During upgrade:**
  1. Node groups upgraded one at a time
  2. PDB ensures `minAvailable: 1` maintained
  3. Monitor for pod restarts and errors
- **After upgrade:**
  1. Verify all pods running: `kubectl get pods -n mcp-kafka-prod`
  2. Run smoke test: call `list_topics` on each cluster
  3. Monitor error rates for 1 hour

**Q6: What's the single-region resiliency plan (DR out of scope)?**

| Scenario | RTO | RPO | Recovery Steps |
|----------|-----|-----|----------------|
| Single pod crash | < 1 min | 0 | HPA spawns replacement automatically |
| Node failure | < 5 min | 0 | Pods rescheduled to other nodes |
| AZ outage | < 5 min | 0 | Multi-AZ deployment, traffic shifts |
| EKS control plane issue | < 15 min | 0 | AWS handles, pods keep running |
| Full region outage | Manual | 0 | Out of scope — no DR region configured |
| MSK cluster failure | N/A | N/A | Out of scope — MSK team handles |

**DR status:** Multi-region failover is not implemented in the current phase.

**Q7: Cost attribution — how do I charge back to teams?**
- Audit logs include `user_email` and `team` claims from JWT
- Monthly report groups usage by team:
  ```sql
  SELECT 
    user_info->>'team' as team,
    COUNT(*) as total_requests,
    COUNT(DISTINCT user_info->>'email') as unique_users
  FROM mcp_audit_log
  WHERE timestamp >= DATE_TRUNC('month', CURRENT_DATE)
  GROUP BY team
  ORDER BY total_requests DESC;
  ```
- Divide monthly infra cost ($1,250) by total requests, multiply by team's requests

---

## 📦 Self-Service Onboarding Flow

```
┌─────────────────────────────────────────────────────────────┐
│ DAY 1: Developer Joining Company                            │
└─────────────────────────────────────────────────────────────┘

09:00 - HR adds developer to Okta
        ↓
09:05 - Developer added to "developers" group (auto)
        ↓
09:10 - Developer receives welcome email:
        "Welcome! Set up your dev environment: 
         https://wiki.company.com/mcp-setup"
        ↓
09:15 - Developer follows wiki guide:
        
        ┌────────────────────────────────────────────────────────────────┐
        │  🚀 MCP Setup Guide (2 steps, ~3 minutes)                     │
        │                                                                │
        │  Step 1: Install MCP Auth Extension                           │
        │  ─────────────────────────────────────────                    │
        │  • Open VS Code                                               │
        │  • Go to Extensions (Ctrl+Shift+X / Cmd+Shift+X)             │
        │  • Search: "Company MCP Auth"                                 │
        │  • Click Install                                              │
        │  • Extension prompts: "Sign in to Company SSO" → Click it    │
        │  • Browser opens → SSO login → Done!                          │
        │                                                                │
        │  Step 2: Add mcp.json                                         │
        │  ─────────────────────────────────────────                    │
        │  • Create file: ~/.vscode/mcp.json                            │
        │  • Copy-paste from below (or download from wiki):             │
        │                                                                │
        │  {                                                            │
        │    "servers": {                                               │
        │      "kafka-dev": {                                           │
        │        "type": "http",                                        │
        │        "url": "https://mcp.company.com/kafka/dev"             │
        │      },                                                       │
        │      "kafka-staging": {                                       │
        │        "type": "http",                                        │
        │        "url": "https://mcp.company.com/kafka/staging"         │
        │      }                                                        │
        │    }                                                          │
        │  }                                                            │
        │                                                                │
        │  That's it! No CLI, no environment variables.                │
        └────────────────────────────────────────────────────────────────┘
        ↓
09:17 - Developer installs extension
        ↓
09:18 - Extension prompts "Sign in to Company SSO"
        Developer clicks → Browser opens
        ↓
09:19 - Developer logs in with company SSO (same creds as Jira)
        ↓
09:20 - Tokens stored automatically in keychain
        Extension shows: "✅ Signed in as john@company.com"
        ↓
09:21 - Developer creates mcp.json (copy-paste from wiki)
        ↓
09:22 - Developer opens Copilot Chat (Ctrl+Shift+I / Cmd+Shift+I)
        Types: "List topics from dev"
        ↓
        Copilot:
        "Found 47 topics on kafka-dev:
         • orders
         • payments
         • users
         • inventory
         • audit-logs
         ... (42 more)
         
         Would you like details on any of these?"
        ↓
09:23 - Developer productive! ✅

Total onboarding time: ~3 minutes
Manual intervention: ZERO ✅
CLI tools required: NONE ✅
```

---

## 🔍 Architecture Decision Records (ADRs)

### ADR-001: Why Python for MCP Server (not Java)?

**Decision:** Use Python with confluent-kafka-python for Kafka MCP servers

**Context:** 
- Need to build remote MCP servers for Kafka administration
- Must support Streamable HTTP transport
- Target: 100-1000 concurrent users

**Rationale:**
- ✅ Official MCP SDK (Anthropic maintains Python SDK)
- ✅ 65% less code than Java (90 lines vs 300 lines)
- ✅ Faster development iterations (no compile step)
- ✅ FastAPI handles HTTP natively
- ✅ confluent-kafka-python has 95% performance of Java (uses librdkafka C library)
- ✅ Easier to hire Python developers for maintenance
- ✅ Faster startup (0.5s vs Java's 2-3s) - better for auto-scaling

**Trade-offs:**
- ❌ Slightly lower throughput than pure Java (90K vs 100K ops/s)
- ✅ But admin operations are <100 ops/s, so difference is negligible

**Status:** Approved

---

### ADR-002: Why Deployment (Not StatefulSet) and Why One Per Cluster?

**Decision:** Use Deployment (not StatefulSet), with one Deployment per Kafka cluster

**Context:**
- Need to deploy Kafka MCP servers in Kubernetes
- Each server connects to one MSK cluster
- Require HA with multiple replicas

**Part 1: Why Deployment over StatefulSet?**

MCP servers are **stateless** — they have no local data. All state is in Kafka (source of truth).

| Feature | StatefulSet | Deployment | Needed for MCP? |
|---------|-------------|------------|------------------|
| Stable pod names (pod-0, pod-1) | ✅ | ❌ | No — any replica can serve any request |
| Ordered startup/shutdown | ✅ | ❌ | No — pods are independent |
| Persistent volumes | ✅ | ❌ | No — no local storage |
| Parallel scaling | ❌ (sequential) | ✅ | **Yes — faster auto-scaling** |
| Parallel rollouts | ❌ (sequential) | ✅ | **Yes — faster deployments** |

**Part 2: Why one Deployment per Kafka cluster?**

| Concern | One MCP → All Clusters | One MCP per Cluster |
|---------|------------------------|---------------------|
| **Security** | Pod has creds for ALL clusters | Pod has creds for ONE cluster only ✅ |
| **Blast radius** | Compromise = all clusters exposed | Compromise = one cluster exposed ✅ |
| **Isolation** | Bug in prod connection affects dev | Environments isolated ✅ |
| **Scaling** | Can't scale prod independently | Scale prod to 5 replicas, keep dev at 2 ✅ |
| **NetworkPolicy** | Can't restrict which pods talk to which clusters | MCP-prod can ONLY talk to Kafka-prod ✅ |

**Result:**
```
kafka-mcp-dev (Deployment, 2 replicas) → Kafka Dev only
kafka-mcp-staging (Deployment, 2 replicas) → Kafka Staging only  
kafka-mcp-prod (Deployment, 3 replicas) → Kafka Prod only
```

**Is this a single point of failure?** No! Each Deployment has 2+ replicas. If one pod dies, others continue serving. Kubernetes restarts failed pods automatically.

**Status:** Approved

---

### ADR-003: Why Kong Gateway (not AWS API Gateway)?

**Decision:** Kong Gateway on Kubernetes (not AWS API Gateway)

**Context:**
- Need API gateway for authentication, rate limiting, routing
- Two options: AWS API Gateway (managed) vs Kong (self-hosted)

**Rationale for Kong:**
- ✅ Runs inside K8s (lower latency, no VPC egress costs)
- ✅ Better plugin ecosystem (OAuth, rate limiting, circuit breaker, custom plugins)
- ✅ Open source, portable (not AWS lock-in, can move to GCP/Azure)
- ✅ Native Kubernetes integration (Ingress controller)
- ✅ Handles HTTP/HTTPS natively
- ✅ Lower cost at scale (no per-request charges)

**AWS API Gateway pros:**
- Fully managed (less operational burden)
- Built-in DDoS protection
- Native AWS integrations

**AWS API Gateway cons:**
- Higher latency (outside VPC, requires VPC Link)
- More expensive at scale ($3.50 per million requests)
- WebSocket support limited
- Less flexible routing

**Decision:** Kong for better control, lower cost, and Kubernetes-native approach

**Status:** Approved

---

### ADR-004: MCP Transport Protocol — Streamable HTTP

**Decision:** Use **Streamable HTTP** as the MCP transport

**Context:**
The MCP protocol defines how VS Code communicates with remote MCP servers. There are three transport options:

| Transport | Use Case |
|-----------|----------|
| **stdio** | Local MCP servers only (subprocess on laptop) |
| **SSE** | Remote servers with long-running operations needing real-time progress |
| **Streamable HTTP** | Remote servers with fast request/response operations |

**Why Streamable HTTP for Kafka MCP:**

Our Kafka operations are fast — no need for streaming:

| Operation | Duration |
|-----------|----------|
| list_topics | ~100ms |
| describe_topic | ~100ms |
| create_topic | ~500ms |
| delete_topic | ~500ms |

**Streamable HTTP advantages:**

| Aspect | SSE | Streamable HTTP |
|--------|-----|-----------------|
| Connection model | Persistent (long-lived) | Stateless (per-request) |
| Complexity | Connection management needed | Simple HTTP POST |
| Load balancing | Tricky (sticky sessions) | Easy (any pod can handle) |
| Firewall/proxy | Sometimes blocked | Always works |
| Scaling | Connection limits matter | Scales naturally |
| Best for | Real-time progress, long operations | Fast request/response |

**Our use case:**
- Operations complete in <500ms
- No real-time progress updates needed
- Simple request → response pattern
- Stateless servers scale better

**Protocol flow:**

```
Streamable HTTP (what we use):

  VS Code                                MCP Server
     │                                       │
     │──POST /kafka/dev ────────────────────→│
     │   Authorization: Bearer <JWT>         │
     │   {"method": "tools/call",            │
     │    "params": {"name": "list_topics"}} │
     │                                       │
     │←─────────────────── JSON response ────│
     │   {"topics": ["orders", "users"]}     │
     │                                       │

  Each request is independent. No persistent connection.
  Load balancer can route to any pod.
```

**mcp.json configuration:**

```json
{
  "servers": {
    "kafka-dev": {
      "type": "http",
      "url": "https://mcp.company.com/kafka/dev"
    }
  }
}
```

**Status:** Approved

---

## 📊 Estimated Costs (AWS Infrastructure)

### Monthly Cost Breakdown (100 developers, 5 Kafka clusters)

> ⚠️ **Note:** These are **realistic estimates** including commonly overlooked costs. Your actual costs may vary based on region, traffic patterns, and usage.

| Component | Specification | Quantity | Unit Cost | Monthly Cost |
|-----------|--------------|----------|-----------|--------------|
| **EKS Control Plane** | Standard | 1 | $73/month | $73 |
| **EC2 Instances (On-Demand)** | m5.xlarge (prod) | 3 | $144/month | $432 |
| **EC2 Instances (Spot)** | m5.large (dev/staging) | 6 | $32/month | $194 |
| **Application Load Balancer** | 1x ALB + LCU hours | 1 | $35/month | $35 |
| **NAT Gateway** | 2 AZs + data processing | 2 | $45/month | $90 |
| **RDS PostgreSQL** | db.t3.medium (audit logs) | 1 | $65/month | $65 |
| **ElastiCache Redis** | cache.t3.small (rate limit) | 1 | $25/month | $25 |
| **CloudWatch Logs** | 100GB/month retention | - | $0.50/GB | $50 |
| **CloudWatch Metrics** | Custom metrics + alarms | - | - | $30 |
| **Secrets Manager** | 15 secrets + API calls | 15 | $0.40/secret | $10 |
| **Route53** | Hosted zone + queries | 1 | $1/month | $1 |
| **VPC Endpoints** | S3, ECR, Secrets, STS, CW | 5 | $7/month | $35 |
| **ECR Storage** | Docker images | - | - | $15 |
| **Cross-AZ Data Transfer** | ~100GB/month | - | $0.01/GB | $30 |
| **AWS MSK** | *Already exists* | - | - | $0 (not counted) |
| | | | **Subtotal** | **$1,085** |
| | | | **+ 15% contingency** | **$163** |
| | | | **Total** | **~$1,250/month** |

**Per developer cost:** $12.50/month per developer

### Commonly Overlooked Costs (Now Included)

| Often Missed | Why It Matters | Estimated Impact |
|--------------|----------------|------------------|
| **NAT Gateway data processing** | Every KB to internet costs $0.045/GB | $50-200/month |
| **Cross-AZ data transfer** | EKS spreads pods across AZs | $30-100/month |
| **ECR storage + pull costs** | Docker image storage and pulls | $15-50/month |
| **CloudWatch custom metrics** | Prometheus → CloudWatch if used | $20-50/month |
| **Load testing environment** | Duplicate infra for testing | $200-500/month |
| **Staging environment** | If full replica needed | $500-800/month |

### Scaling Projections (Realistic)

| Users | Monthly Cost | Per User | Notes |
|-------|--------------|----------|-------|
| 100 | $1,250 | $12.50 | Current realistic estimate |
| 250 | $1,600 | $6.40 | Same infra, more users |
| 500 | $2,400 | $4.80 | Add 2 nodes, scale Redis to t3.medium |
| 1000 | $3,500 | $3.50 | Add 5 nodes, multi-AZ Redis, larger ALB |

### Cost Optimizations Applied
- ✅ Spot instances for dev/staging (50% savings)
- ✅ Right-sized pods (512MB instead of 1GB)
- ✅ Auto-scaling down nights/weekends (potential 30% savings)
- ✅ VPC Endpoints reduce NAT Gateway costs

### Cost Optimizations NOT Yet Applied (Future)
- ⬜ Reserved Instances / Savings Plans (40% savings on EC2)
- ⬜ Graviton instances (20% cheaper, requires ARM builds)
- ⬜ Karpenter auto-provisioner (right-size nodes dynamically)

---

## 🚨 Critical Items Before Staging (Must-Do Checklist)

> **WARNING:** Do NOT deploy to staging until ALL P0 items are complete. These are non-negotiable for production readiness.

### P0 — Blocking (Must complete before any staging deployment)

| # | Item | Risk if Skipped | Effort | Owner |
|---|------|-----------------|--------|-------|
| 1 | **Kong JWKS configuration pointing to PingFederate** | JWT validation fails, all requests rejected | 4 hours | DevOps |
| 2 | **Extension implements `vscode.AuthenticationProvider`** | Auth won't integrate with VS Code natively | 2 days | Extension team |
| 3 | **Token storage: keychain (persistence) + memory (speed)** | Slow auth or lost tokens on restart | 1 day | Extension team |
| 4 | **PodDisruptionBudget on all Deployments** | Outage during upgrades | 2 hours | DevOps |
| 5 | **NetworkPolicy: MCP pods only accept traffic from Kong** | Lateral movement if breach | 4 hours | DevOps |
| 6 | **Circuit breaker configured in Kong** | Cascade failure to Kafka | 1 day | DevOps |
| 7 | **Graceful shutdown (preStop + terminationGracePeriod=60s)** | Dropped in-flight requests | 2 hours | DevOps |
| 8 | **mcp.json contains ONLY URLs (no tokens)** | If tokens in mcp.json, credential leak risk | 1 day | Extension team |
| 9 | **All runbooks written and reviewed** | Incident response failure | 3 days | SRE |

### P1 — Required (Must complete before production GA)

| # | Item | Risk if Skipped | Effort | Owner |
|---|------|-----------------|--------|-------|
| 10 | **Silent token refresh (using refresh token)** | Users prompted every hour | 1 day | Extension team |
| 11 | **Token refresh retry with exponential backoff** | Transient PingFed failures = auth failure | 4 hours | Extension team |
| 12 | **30-day inactive re-sign-in prompt** | Users see cryptic errors after long absence | 4 hours | Extension team |
| 13 | **Corporate proxy support in extension** | Won't work for many devs behind proxy | 2 days | Extension team |
| 14 | **Kong rate limiting per user (sub claim)** | Single user can overload backend | 1 day | DevOps |
| 15 | **PingFederate health monitoring + alerting** | Silent auth failures | 4 hours | SRE |
| 16 | **Load test at 2x target scale (200 concurrent users)** | Capacity surprises in prod | 2 days | QA |
| 17 | **Security penetration test completed** | Vulnerabilities in prod | 1 week | Security |
| 18 | **Rollback procedure documented and tested in dev** | Can't recover from bad deploy | 1 day | DevOps |

### P2 — Recommended (Address after stable GA)

| # | Item | Benefit | Effort |
|---|------|---------|--------|
| 19 | ~~Consider Deployment over StatefulSet~~ | ✅ Done — using Deployment | — |
| 20 | **Extension status bar indicator (connected/disconnected)** | Better UX | 4 hours |
| 21 | **Admin dashboard for access management** | Operational efficiency | 1 week |
| 22 | **Automated Kafka credential rotation** | Reduce blast radius | 3 days |
| 23 | **Multi-region deployment** | Out of scope for now | 2 weeks |
| 24 | **Karpenter for node autoscaling** | Cost optimization | 1 week |
| 25 | **Metrics dashboard (Grafana)** | Observability | 2 days |

### Staging Gate Criteria

**Before deploying to staging, confirm:**

- [ ] All P0 items marked complete above
- [ ] Extension tested: install → sign-in prompt → SSO flow → token stored → MCP request works
- [ ] Kong validates JWT correctly (test with valid/invalid/expired tokens)
- [ ] Load test results reviewed (p99 latency < 500ms at 100 users)
- [ ] Security team sign-off obtained
- [ ] Rollback procedure tested in dev
- [ ] On-call rotation established
- [ ] Incident response Slack channel created (`#mcp-incidents`)
- [ ] PingFederate team notified of expected token load
- [ ] mcp.json template documented in wiki (URLs only, no secrets)

---

## 🚀 Implementation Roadmap

### Phase 1: Foundation (Weeks 1-4)

**Week 1-2: Infrastructure Setup**

| Task | Details | Owner |
|------|---------|-------|
| Provision EKS cluster | Multi-AZ (3 zones), managed node groups | DevOps |
| Setup VPC | Private subnets for MCP pods, public for ALB | DevOps |
| Deploy Kong Gateway | Kong Ingress Controller on EKS | DevOps |
| Configure Kong JWKS | Point to PingFederate `/pf/JWKS`, cache TTL 5 min | DevOps |
| Configure Kong JWT plugin | Validate signature, check `exp`, extract `sub` for rate limiting | DevOps |
| Setup ALB | HTTPS termination, route to Kong | DevOps |
| PingFederate OAuth client | Register `mcp-vscode` client (public, PKCE, redirect to localhost) | Identity team |
| Setup audit logging | CloudWatch Logs + S3 archival | DevOps |

**Week 3-4: Kafka MCP Server + VS Code Extension**

| Task | Details | Owner |
|------|---------|-------|
| Build Kafka MCP server | Python + FastAPI + confluent-kafka, Streamable HTTP transport | Backend |
| Implement MCP tools | `list_topics`, `describe_topic`, `create_topic`, `delete_topic`, etc. | Backend |
| Create Docker image | Multi-stage build, non-root user, push to ECR | Backend |
| Deploy to EKS | Deployment (not StatefulSet), 2 replicas, HPA | DevOps |
| Build VS Code extension | Implement `vscode.AuthenticationProvider` (~150 lines) | Extension team |
| Extension: PKCE flow | Auth code + PKCE, localhost callback, browser redirect | Extension team |
| Extension: Token storage | Keychain for persistence, memory cache for speed | Extension team |
| Extension: Silent refresh | Use refresh token, update memory + keychain | Extension team |
| Configure Kong routing | `/kafka/dev/*` → `kafka-mcp-dev-service:8000` | DevOps |
| Document mcp.json | Wiki page with URL-only config (no tokens) | Extension team |

**Week 4: Integration Testing**

| Test | Expected Result |
|------|-----------------|
| Install extension | Sign-in prompt appears immediately |
| Click "Sign In" | Browser opens PingFederate SSO |
| Complete SSO | Token stored, extension shows "Connected" |
| "List topics from dev" in Copilot | Returns topic list from Kafka dev cluster |
| Wait 1+ hour | Token refreshes silently (no prompt) |
| Invalid/expired token | 401 from Kong, extension refreshes |
| User without access | 403 Forbidden |

**Phase 1 Deliverables:**
- ✅ Kong Gateway deployed with JWKS validation
- ✅ Kafka MCP server (dev) accessible via Streamable HTTP
- ✅ VS Code extension: install → sign-in → works
- ✅ mcp.json documented (URLs only)
- ✅ All P0 checklist items complete

---

### Phase 2: Multi-Environment & Hardening (Weeks 5-8)

**Week 5-6: Multi-Environment Kafka**

| Task | Details | Owner |
|------|---------|-------|
| Deploy kafka-mcp-staging | Same image, different K8s Secret (MSK staging creds) | DevOps |
| Deploy kafka-mcp-prod | 3 replicas (HA), stricter resource limits | DevOps |
| Kong routes | `/kafka/staging/*`, `/kafka/prod/*` | DevOps |
| NetworkPolicy | Each MCP pod can ONLY talk to its assigned Kafka cluster | DevOps |
| PodDisruptionBudget | `minAvailable: 1` on all Deployments | DevOps |
| Rate limiting | Kong rate-limit plugin, per-user (sub claim), different limits per env | DevOps |

**Week 7-8: Production Hardening**

| Task | Details | Owner |
|------|---------|-------|
| Circuit breaker | Kong circuit breaker plugin, trip at 50% error rate | DevOps |
| Graceful shutdown | `preStop` hook + `terminationGracePeriod: 60s` | DevOps |
| Monitoring | Prometheus metrics, Grafana dashboard | SRE |
| Alerting | PagerDuty for p99 > 1s, error rate > 5% | SRE |
| Runbooks | Troubleshooting guides for common issues | SRE |
| Extension: 30-day prompt | Clear message when refresh token expires | Extension team |
| Extension: Proxy support | Respect VS Code proxy settings | Extension team |
| Load test | 200 concurrent users, p99 < 500ms | QA |

**Phase 2 Deliverables:**
- ✅ 4 Kafka environments: dev, sit, uat, prod
- ✅ Environment isolation (NetworkPolicy)
- ✅ Rate limiting active
- ✅ Monitoring + alerting in place
- ✅ All P1 checklist items complete

---

### Phase 3: Additional MCP Servers & GA (Weeks 9-12)

**Week 9-10: Database MCP Server**

| Task | Details | Owner |
|------|---------|-------|
| Build Database MCP server | Python + psycopg2, tools: `list_tables`, `describe_table`, `run_query` | Backend |
| Deploy (4 envs) | database-mcp-dev, sit, uat, prod | DevOps |
| Kong routes | `/database/dev/*`, `/database/sit/*`, etc. | DevOps |
| Update mcp.json template | Add database server URLs | Extension team |
| Test | Same JWT token works for Database MCP (no new sign-in) | QA |

**Week 11-12: Production Rollout**

| Task | Details | Owner |
|------|---------|-------|
| Beta rollout | 20 users, collect feedback | Product |
| Security pen test | External firm or internal red team | Security |
| Fix issues | Address findings from beta + pen test | All |
| Company announcement | All-hands, Slack, wiki | Product |
| Gradual rollout | 50 → 100 → all developers | Product |
| Support process | `#mcp-support` Slack channel, on-call rotation | SRE |

**Phase 3 Deliverables:**
- ✅ Kafka + Database MCP servers in production
- ✅ 100+ developers onboarded
- ✅ Security pen test passed
- ✅ On-call rotation established
- ✅ All P2 checklist items addressed

---

### Timeline Summary

```
Week 1-2    Week 3-4    Week 5-6    Week 7-8    Week 9-10   Week 11-12
────────────────────────────────────────────────────────────────────────
│ Infra    │ MCP +     │ Multi-env │ Hardening │ Database  │ GA       │
│ Setup    │ Extension │ Kafka     │ + Monitor │ MCP       │ Rollout  │
────────────────────────────────────────────────────────────────────────
         PHASE 1                  PHASE 2                 PHASE 3
        (Foundation)          (Hardening)              (Scale)
```

**Team Size:** 2 engineers (1 Backend/MCP, 1 DevOps) + part-time Extension dev
**Total Duration:** 12 weeks
**Monthly Cost (at GA):** ~$1,250 for 100 users


## 📚 Open Questions (For Discussion)

### Security & Compliance

**Q1:** SSO Provider: **PingFederate** (confirmed)

**Q2:** Do you need multi-factor authentication (MFA) for production access?
- [ ] Yes, MFA required for all prod access
- [ ] No, SSO password is sufficient
- [ ] Only for admin operations (create/delete topics)

**Q3:** What's your data retention policy for audit logs?
- [ ] 30 days
- [ ] 90 days (recommended)
- [ ] 1 year
- [ ] 7 years (financial services compliance)

**Q4:** Do you need approval workflow for destructive operations (delete topic)?
- [ ] Yes, JIRA/ServiceNow ticket required
- [ ] Yes, manager approval via email
- [ ] No, Kafka admins can delete without approval
- [ ] Only for production clusters

---

### Infrastructure & Operations

**Q5:** Which AWS region(s) for EKS cluster?
- Primary region: _______________

**Note:** Multi-region DR is out of scope for the current phase.

**Q6:** What's your MSK cluster naming convention?
- Example: `{environment}-kafka-{region}` or `kafka-{env}-{region}`
- Your convention: _______________

**Q7:** How many Kafka clusters do you have today?
- Dev clusters: _______
- Staging clusters: _______
- Prod clusters: _______
- Analytics clusters: _______
- Total: _______

**Q8:** Do you use Kafka ACLs or IAM authentication for MSK?
- [ ] IAM authentication (recommended)
- [ ] Kafka ACLs (SASL/SCRAM)
- [ ] Both
- [ ] Unsure

---

### Developer Experience

**Q9:** Onboarding flow: **VS Code extension + PingFederate SSO** (confirmed, no CLI tool, no portal)

**Q10:** Should developers have different access levels?
- [ ] Yes: Junior devs (dev only), Senior devs (dev+staging), Leads (all)
- [ ] No: All developers get same access
- [ ] Team-based: Backend team (backend clusters), Data team (data clusters)

**Q11:** How many developers will use this?
- Current headcount: _______
- Expected in 6 months: _______
- Expected in 1 year: _______

---

### Monitoring & Operations

**Q12:** What's your monitoring stack?
- [ ] Prometheus + Grafana
- [ ] Datadog
- [ ] New Relic
- [ ] CloudWatch only
- [ ] Other: _______________

**Q13:** Who will be on-call for MCP platform issues?
- [ ] Data platform team
- [ ] DevOps/SRE team
- [ ] Kafka admin team
- [ ] Shared rotation
- [ ] Other: _______________

**Q14:** What's your incident response SLA?
- Critical incident response time: _______ minutes
- Non-critical incident response time: _______ hours

---

### Future Enhancements

**Q15:** Which additional MCP servers do you plan to add?
- [ ] PostgreSQL
- [ ] MySQL
- [ ] MongoDB
- [ ] Redis
- [ ] S3
- [ ] Elasticsearch

**Q16:** Do you need RAG/memory layer (conversation history)?
- [ ] Yes, implement in Phase 1
- [ ] Yes, but Phase 2 or later
- [ ] No, stateless is fine

**Q17:** Do you need approval workflows integrated with?
- [ ] JIRA
- [ ] ServiceNow
- [ ] PagerDuty
- [ ] Slack (manual approval)
- [ ] None needed

---

## 📖 Additional Resources

### Reference Documentation
- [MCP Specification](https://spec.modelcontextprotocol.io)
- [MCP Python SDK](https://github.com/modelcontextprotocol/python-sdk)
- [Confluent Kafka Python](https://docs.confluent.io/kafka-clients/python/current/overview.html)
- [AWS MSK IAM Authentication](https://docs.aws.amazon.com/msk/latest/developerguide/iam-access-control.html)
- [EKS IAM Roles for Service Accounts](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html)
- [Kong Gateway Documentation](https://docs.konghq.com/)

### Internal Documentation (To Be Created)
- [ ] Runbook: MCP Server Deployment
- [ ] Runbook: Adding New MCP Server (behind gateway, zero auth code)
- [ ] Runbook: Adding New Kafka Cluster
- [ ] Runbook: Troubleshooting Connection Issues
- [ ] Runbook: Revoking User Access
- [ ] Developer Guide: MCP Usage via VS Code
- [ ] Admin Guide: Managing MCP Platform
- [ ] VS Code Extension: Build & Publish Guide

### Architecture Diagrams (To Be Created)
- [ ] Network diagram (VPC, subnets, security groups)
- [ ] Data flow diagram (request lifecycle)
- [ ] Deployment diagram (K8s namespaces, pods)
- [ ] Security model diagram (authentication flow)

---

## ✅ Next Steps

1. **Review this document** with your team (architects, security, DevOps)
2. **Answer open questions** (see section above)
3. **Prioritize requirements** (must-have vs nice-to-have)
4. **Define timeline** (aggressive vs conservative)
5. **Allocate resources** (engineers, budget)
6. **Start Phase 1** (infrastructure setup)

**Estimated Timeline:** 12 weeks for full production rollout (reduced from 16 — no portal/CLI to build)

**Team Size:** 2 engineers (1 backend/MCP, 1 DevOps/extension)

**Budget:** ~$1,250/month for 100 users (scales to ~$3,500/month for 1000 users)

---

## 📝 Document Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-12 | Senior Architect | Initial architecture document |
| 1.1 | 2026-02-13 | Senior Architect | **Production Hardening Update:** Circuit breaker, PodDisruptionBudget, NetworkPolicy, graceful shutdown, realistic cost estimates ($1,250/month), Critical Items checklist |
| 1.2 | 2026-02-14 | Senior Architect | **MCP Native Auth Simplification:** mcp.json contains only server URLs (no tokens). Extension reduced to ~150 lines. |
| 1.3 | 2026-02-14 | Senior Architect | **Auth Accuracy Update:** Clarified token caching pattern (keychain for persistence, memory for speed). Extension handles token refresh. Sign-in prompts immediately on install. |
| 1.4 | 2026-02-14 | Senior Architect | **Transport Decision:** Streamable HTTP over SSE. Kafka operations are fast (<500ms), no need for persistent connections or streaming. Simpler, stateless, scales better. |
| 1.5 | 2026-02-14 | Senior Architect | **End-to-End Request Flow:** Added comprehensive section with visual diagrams showing how requests flow from chat to response, multi-server routing examples (Kafka vs Database), and visual architecture summary. |
| 1.6 | 2026-02-14 | Senior Architect | **mcp.json Reference:** Complete multi-environment configuration (dev, sit, uat, prod) for Kafka and Database with URL pattern breakdown and Copilot interaction examples. |
| 1.7 | 2026-02-14 | Senior Architect | **JWKS JWT Validation:** Added detailed section explaining how Kong validates JWT using cached JWKS public keys (no per-request calls to PingFed). Includes RSA crypto explanation, Kong plugin config, and security boundary diagram. |
| 1.8 | 2026-02-14 | Senior Architect | **Token Lifecycle:** Added detailed explanation of access token (1hr) vs refresh token (30 days), silent refresh flow, and 30-day inactive scenario with visual timeline. |
| 1.9 | 2026-02-14 | Senior Architect | **Persona Q&A Refresh:** Updated Developer Q&A (removed CLI, added extension-based onboarding, Q12 on mcp.json auth). Added Architect Q6-Q9 (Kong vs AWS Gateway, Streamable HTTP, JWKS, Copilot routing). Updated Self-Service Onboarding flow to match new architecture. |
| 2.0 | 2026-02-14 | Senior Architect | **Deployment over StatefulSet:** MCP servers are stateless — changed from StatefulSet to Deployment. Rewrote ADR-002 explaining why Deployment is correct (faster scaling, parallel rollouts) and why one Deployment per cluster (security isolation, independent scaling). Updated all K8s manifests and kubectl commands. |
| 2.1 | 2026-02-14 | Senior Architect | **Critical Items Checklist Revised:** P0 now includes Kong JWKS config, AuthenticationProvider implementation, token storage pattern, mcp.json URL-only requirement. P1 adds silent refresh, 30-day re-sign-in, rate limiting. Staging gate criteria expanded. |
| 2.2 | 2026-02-14 | Senior Architect | **Implementation Roadmap Revised:** Converted to tabular format with Owner assignments. Phase 1 now specifies JWKS config, AuthenticationProvider, Deployment manifests, integration tests. Phase 2 adds NetworkPolicy, PodDisruptionBudget, circuit breaker. Phase 3 adds security pen test, gradual rollout plan. Added timeline summary visual. |
| 2.3 | 2026-02-14 | Senior Architect | **Project Checklist Aligned:** Updated API Gateway section to Kong only (not Nginx). Added JWKS TTL, circuit breaker, multi-environment routes (dev/sit/uat/prod). Token management now includes 30-day re-sign-in prompt with clear message. K8s Deployment section expanded with specific resource limits, probe configs, PDB, NetworkPolicy. Per-cluster deployments updated to match 4-environment model. |
| 2.4 | 2026-02-14 | Senior Architect | **Architecture Diagrams Added:** Added 6 Mermaid diagrams for different stakeholders: (1) AWS Infrastructure Topology showing VPC, EKS, MSK, Kong placement; (2) Security Boundary diagram with trust zones; (3) End-to-End Request Flow sequence diagram with timing; (4) VS Code Extension Component diagram showing two-tier storage; (5) Token Lifecycle state diagram with 30-day scenario; (6) User Journey Map comparing before/after experience. |

---

## ✅ Project Checklist (Track Progress)

Use this checklist to track all work items. Mark items `[x]` as they are completed.

---

### 1. PingFederate SSO Configuration

- [ ] **1.1 Register OAuth Client in PingFederate**
  - [ ] Client ID: `mcp-vscode` (public client, no secret)
  - [ ] Grant Type: Authorization Code + PKCE (S256)
  - [ ] Redirect URI: `http://localhost:*/callback` (wildcard port)
  - [ ] Scopes: `openid`, `profile`, `email`, `mcp-access`
  - [ ] Access Token Type: JWT (RS256 signed)
  - [ ] Access Token TTL: 1 hour
  - [ ] Refresh Token TTL: 30 days
- [ ] **1.2 JWT Claims Mapping**
  - [ ] `sub` — user unique ID
  - [ ] `email` — user email
  - [ ] `teams` — team membership (from AD/LDAP groups)
  - [ ] `roles` — role assignments
  - [ ] `aud` — set to `mcp-api` (single audience for all MCP servers)
- [ ] **1.3 JWKS Endpoint Accessible**
  - [ ] `https://sso.company.com/pf/JWKS` reachable from AWS VPC
  - [ ] API Gateway can fetch and cache JWKS public keys

---

### 2. VS Code Auth Extension

- [ ] **2.1 Project Setup**
  - [ ] Scaffold TypeScript extension project
  - [ ] Configure `package.json` (commands, settings, authentication contribution point)
  - [ ] Setup build pipeline (compile, test, package)
- [ ] **2.2 Authentication Provider**
  - [ ] Implement `vscode.AuthenticationProvider` (provider ID: `company-sso`)
  - [ ] PKCE code_verifier + code_challenge generation
  - [ ] Local HTTP callback server (127.0.0.1, random port, 5-min timeout)
  - [ ] Open browser via `vscode.env.openExternal()` for SSO login
  - [ ] Exchange authorization code for tokens (POST to token endpoint)
  - [ ] State parameter for CSRF protection
- [ ] **2.3 Token Management**
  - [ ] Read tokens from keychain once at startup → cache in memory
  - [ ] Return session from memory on `getSessions()` calls
  - [ ] Refresh expired access token using refresh token (silent, no prompt)
  - [ ] Update memory cache + keychain after refresh
  - [ ] Prompt re-sign-in if refresh token expired (30-day inactive scenario)
  - [ ] Clear message: "Your session has expired. Please sign in again."
  - [ ] Clear all tokens on sign-out (`removeSession()`)
- [ ] **2.4 mcp.json (Static URLs Only)**
  - [ ] Document required mcp.json structure (URLs only, no tokens)
  - [ ] Optional: Auto-generate from server discovery endpoint
- [ ] **2.5 Settings & Config**
  - [ ] `mcpAuth.ssoUrl` setting (PingFederate base URL)
  - [ ] `mcpAuth.clientId` setting (OAuth client ID)
  - [ ] `mcpAuth.scopes` setting (default: `openid profile email mcp-access`)
- [ ] **2.6 UI**
  - [ ] Status bar item (connected/disconnected/reconnecting)
  - [ ] Sign-in notification with action button
  - [ ] Extension appears in VS Code Accounts menu
- [ ] **2.7 Testing**
  - [ ] First-time sign-in flow works
  - [ ] Token returned correctly when VS Code requests session
  - [ ] Sign-out clears everything
  - [ ] Works on macOS, Windows, Linux
  - [ ] No impact on existing extensions (Copilot, etc.)
- [ ] **2.8 Distribution**
  - [ ] Package as `.vsix`
  - [ ] Publish to internal Marketplace (or direct `.vsix` distribution)
  - [ ] Verify auto-update works

---

### 3. AWS Infrastructure

- [ ] **3.1 Networking**
  - [ ] VPC with private subnets
  - [ ] Security groups for MCP server pods
  - [ ] NAT Gateway for outbound access (PingFederate JWKS, MSK)
- [ ] **3.2 EKS Cluster**
  - [ ] Provision EKS cluster
  - [ ] Create `mcp-system` namespace
  - [ ] Configure node groups (auto-scaling)
  - [ ] Setup IRSA (IAM Roles for Service Accounts)
- [ ] **3.3 API Gateway (Kong on EKS)**
  - [ ] Deploy Kong Ingress Controller in EKS
  - [ ] Configure JWT validation plugin (JWKS caching, TTL 5 min)
  - [ ] Point JWKS to PingFederate (`/pf/JWKS`)
  - [ ] Configure rate limiting plugin (per user, based on `sub` claim)
  - [ ] Configure circuit breaker plugin (trip at 50% error rate)
  - [ ] Configure audit logging
  - [ ] TLS termination (certificate from ACM)
  - [ ] Route: `/kafka/dev/*` → `kafka-mcp-dev-service:8000`
  - [ ] Route: `/kafka/sit/*` → `kafka-mcp-sit-service:8000`
  - [ ] Route: `/kafka/uat/*` → `kafka-mcp-uat-service:8000`
  - [ ] Route: `/kafka/prod/*` → `kafka-mcp-prod-service:8000`
  - [ ] Route: `/database/dev/*` → `database-mcp-dev-service:8000` (Phase 3)
  - [ ] Strip path prefix (MCP server receives requests at `/`)
- [ ] **3.4 ALB / Ingress**
  - [ ] Provision Application Load Balancer
  - [ ] DNS: `mcp.company.com` → ALB
  - [ ] TLS certificate attached
- [ ] **3.5 ECR (Container Registry)**
  - [ ] Create ECR repository for MCP server images
  - [ ] Push Docker images
  - [ ] Image scanning enabled

---

### 4. Kafka MCP Server (Remote / Python)

- [ ] **4.1 Build MCP Server**
  - [ ] Python project setup (FastAPI + confluent-kafka-python)
  - [ ] Implement MCP JSON-RPC protocol (single endpoint `/`)
  - [ ] `initialize` handler (capabilities, server info)
  - [ ] `tools/list` handler (return all Kafka tools)
  - [ ] `tools/call` dispatcher (route by tool name)
- [ ] **4.2 Kafka Tools Implementation**
  - [ ] `list_topics` — list all Kafka topics
  - [ ] `describe_topic` — topic details (partitions, replicas, config)
  - [ ] `create_topic` — create new topic
  - [ ] `delete_topic` — delete topic (with safety checks)
  - [ ] `update_topic` — update partition count
  - [ ] `topic_exists` — check if topic exists
  - [ ] `cluster_overview` — cluster health and metadata
- [ ] **4.3 Transport**
  - [ ] Streamable HTTP transport working (VS Code `"type": "http"`)
- [ ] **4.4 Containerization**
  - [ ] Dockerfile (multi-stage build)
  - [ ] Health check endpoint (`/health`)
  - [ ] Non-root user in container
  - [ ] Image size optimized
- [ ] **4.5 Kubernetes Deployment**
  - [ ] Deployment manifest (Deployment, not StatefulSet — MCP is stateless)
  - [ ] Service (ClusterIP, port 8000)
  - [ ] Resource limits (CPU: 500m-1000m, Memory: 256Mi-512Mi)
  - [ ] Liveness probe (`/health`, initial delay 10s)
  - [ ] Readiness probe (`/health`, initial delay 5s)
  - [ ] HPA (min: 2, max: 10, CPU target: 70%)
  - [ ] PodDisruptionBudget (`minAvailable: 1`)
  - [ ] NetworkPolicy (egress only to assigned Kafka cluster)
- [ ] **4.6 Per-Cluster Deployments**
  - [ ] `kafka-mcp-dev` Deployment → connected to Dev MSK cluster
  - [ ] `kafka-mcp-sit` Deployment → connected to SIT MSK cluster
  - [ ] `kafka-mcp-uat` Deployment → connected to UAT MSK cluster
  - [ ] `kafka-mcp-prod` Deployment → connected to Prod MSK cluster (3 replicas)
  - [ ] Each Deployment uses separate K8s Secret for cluster credentials
  - [ ] NetworkPolicy ensures each Deployment can ONLY reach its assigned cluster
- [ ] **4.7 Zero Auth Code**
  - [ ] Server has NO authentication code
  - [ ] Server receives pre-authenticated requests from gateway
  - [ ] User identity available via `X-User-Email` / `X-User-Teams` headers

---

### 5. Amazon MSK (Kafka Clusters)

- [ ] **5.1 Cluster Provisioning**
  - [ ] Dev cluster provisioned
  - [ ] Staging cluster provisioned
  - [ ] Prod cluster provisioned
  - [ ] Additional regional clusters provisioned (if applicable)
- [ ] **5.2 Security**
  - [ ] IAM authentication enabled for MSK
  - [ ] VPC peering / PrivateLink between EKS and MSK
  - [ ] Encryption in transit (TLS)
  - [ ] Encryption at rest
- [ ] **5.3 IAM Roles**
  - [ ] `kafka-mcp-dev-role` — read-only on dev cluster
  - [ ] `kafka-mcp-staging-role` — read-only on staging cluster
  - [ ] `kafka-mcp-prod-role` — read-only on prod cluster (admin ops restricted)
  - [ ] IRSA configured (pod → IAM role mapping)

---

### 6. Monitoring & Observability

- [ ] **6.1 Metrics**
  - [ ] Prometheus scraping MCP server pods
  - [ ] Grafana dashboard for MCP server health
  - [ ] Request latency metrics (p50, p95, p99)
  - [ ] Active connections per server
  - [ ] Tool call counts (by tool name)
  - [ ] Error rates
- [ ] **6.2 Logging**
  - [ ] Structured JSON logging from MCP servers
  - [ ] Audit log: who called what tool, when, on which cluster
  - [ ] Log aggregation (CloudWatch / ELK / Datadog)
  - [ ] NEVER log JWT tokens or credentials
- [ ] **6.3 Alerting**
  - [ ] Alert: MCP server error rate > 5%
  - [ ] Alert: Response latency p95 > 2 seconds
  - [ ] Alert: Pod restart count > 3 in 10 minutes
  - [ ] Alert: PingFederate JWKS refresh failure

---

### 7. Security & Compliance

- [ ] **7.1 JWT Validation**
  - [ ] Gateway validates JWT signature (cached JWKS, no per-request SSO call)
  - [ ] Gateway checks `exp` (token not expired)
  - [ ] Gateway checks `aud` (must be `mcp-api`)
  - [ ] Gateway checks `iss` (must be PingFederate)
- [ ] **7.2 Network Security**
  - [ ] MCP server pods NOT directly accessible from internet
  - [ ] All traffic through API Gateway only
  - [ ] MSK clusters in private subnets
  - [ ] Security group rules: gateway → MCP pods, MCP pods → MSK only
- [ ] **7.3 Data Protection**
  - [ ] No Kafka message content exposed via MCP tools (metadata only)
  - [ ] Prod cluster: destructive operations blocked or require approval
  - [ ] Rate limiting active (per-user)
- [ ] **7.4 Compliance Review**
  - [ ] Security team review completed
  - [ ] Penetration testing completed
  - [ ] Compliance audit passed (SOC2 / ISO27001 if applicable)

---

### 8. Documentation & Runbooks

- [ ] **8.1 Architecture Documentation**
  - [ ] This document reviewed and approved by team
  - [ ] Architecture diagrams finalized
  - [ ] ADRs reviewed and signed off
- [ ] **8.2 Developer Documentation**
  - [ ] Extension installation guide
  - [ ] "Getting started" guide (install + sign in + first query)
  - [ ] Troubleshooting FAQ
  - [ ] Available MCP tools reference (all Kafka tools documented)
- [ ] **8.3 Operations Runbooks**
  - [ ] MCP server pod not starting — troubleshooting steps
  - [ ] PingFederate SSO integration issues
  - [ ] Adding a new Kafka cluster — step-by-step
  - [ ] Adding a new MCP server type — step-by-step
  - [ ] Incident response for MCP service outage
- [ ] **8.4 Internal Team Docs**
  - [ ] VS Code extension build & publish guide
  - [ ] MCP server development guide (how to add new tools)
  - [ ] Gateway configuration guide

---

### 9. Testing & QA

- [ ] **9.1 Unit Tests**
  - [ ] VS Code extension: PKCE generation, state validation, callback handling
  - [ ] MCP server: each tool function tested individually
- [ ] **9.2 Integration Tests**
  - [ ] End-to-end: VS Code → Gateway → MCP Server → Kafka
  - [ ] Auth flow: install → sign-in prompt → session created → tool call → result
  - [ ] Token refresh: expired token → silent refresh → request succeeds
- [ ] **9.3 Load Testing**
  - [ ] 100 concurrent users simulated
  - [ ] 1,000 concurrent users simulated (target scale)
  - [ ] Gateway rate limiting validated under load
- [ ] **9.4 Security Testing**
  - [ ] Expired token rejected by gateway
  - [ ] Invalid signature rejected by gateway
  - [ ] Wrong audience rejected by gateway
  - [ ] Tokens never logged anywhere
  - [ ] Callback server binds to 127.0.0.1 only

---

### 10. Rollout & Onboarding

- [ ] **10.1 Beta Rollout (10 users)**
  - [ ] Select 10 beta users from different teams
  - [ ] Distribute extension (`.vsix` or Marketplace)
  - [ ] Collect feedback (Slack channel or survey)
  - [ ] Fix critical issues
- [ ] **10.2 Expanded Rollout (50 users)**
  - [ ] Onboard 50 users
  - [ ] Monitor metrics (error rates, latency, adoption)
  - [ ] Refine documentation based on support questions
- [ ] **10.3 General Availability (100–1,000 users)**
  - [ ] Announce to engineering organization (all-hands, email, Slack)
  - [ ] Extension available on internal Marketplace
  - [ ] Onboarding guide distributed
  - [ ] Support channel established (Slack `#mcp-support`)
  - [ ] Monitor scaling (auto-scaling, PingFederate load)
- [ ] **10.4 Post-Launch**
  - [ ] 30-day adoption metrics review
  - [ ] User satisfaction survey
  - [ ] Performance tuning based on real usage
  - [ ] Plan Phase 2 MCP servers (Database, Redis, etc.)

---

### Progress Summary

| Area | Items | Completed | Status |
|------|-------|-----------|--------|
| 1. PingFederate SSO | 3 main / 13 sub | 0 | 🔴 Not started |
| 2. VS Code Extension | 8 main / 31 sub | 0 | 🔴 Not started |
| 3. AWS Infrastructure | 5 main / 22 sub | 0 | 🔴 Not started |
| 4. Kafka MCP Server | 7 main / 28 sub | 0 | 🔴 Not started |
| 5. Amazon MSK | 3 main / 11 sub | 0 | 🔴 Not started |
| 6. Monitoring | 3 main / 12 sub | 0 | 🔴 Not started |
| 7. Security | 4 main / 13 sub | 0 | 🔴 Not started |
| 8. Documentation | 4 main / 13 sub | 0 | 🔴 Not started |
| 9. Testing | 4 main / 14 sub | 0 | 🔴 Not started |
| 10. Rollout | 4 main / 13 sub | 0 | 🔴 Not started |
| **TOTAL** | **45 main / 170 sub** | **0** | 🔴 **Not started** |

Update this summary table as items are completed. Change status to:
- 🟡 In progress (some items done)
- 🟢 Complete (all items done)

---

**Questions or need clarification?** Add your questions in comments or create GitHub issues for discussion.
