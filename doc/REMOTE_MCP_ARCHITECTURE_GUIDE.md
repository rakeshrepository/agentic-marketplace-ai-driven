# Enterprise Remote MCP Architecture - Kafka Administration via VS Code

**Author:** Senior Architect  
**Date:** February 12, 2026  
**Purpose:** Production-grade architecture for deploying Kafka MCP servers as remote services, accessible exclusively through VS Code IDE

---

## 🎯 Executive Summary

This document provides a complete architecture for deploying Kafka MCP servers as **remote services** that developers access exclusively through **VS Code IDE** on their local laptops.

### What This Is

A remote MCP server infrastructure that allows any developer in the company to manage Kafka clusters by simply talking to Claude/Copilot in VS Code — no Kafka CLI tools, no web portals, no terminal commands. Just natural language in the IDE.

### How Developers Interact

**Single entry point:** VS Code on the developer's laptop. No web portals, no CLI tools, no separate applications.

```
Developer's Laptop (VS Code)
    → Opens Copilot Chat
    → Types: "List all topics on dev Kafka cluster"
    → Claude calls remote MCP server (behind the scenes)
    → Results displayed in VS Code
```

### Key Design Decisions

- **VS Code only** — All interaction happens inside the IDE. No web portals or external tools
- **PingFederate SSO** — Authentication uses the same company SSO that developers already use for Jira, Confluence, etc. Sign in once (browser popup, identical to GitHub Copilot sign-in), then never think about it again
- **VS Code extension for auth** — A lightweight extension handles authentication, token storage, and silent token refresh (same pattern as GitHub Copilot)
- **3 Kafka clusters** — Dev, Staging, Production (architecture supports adding more in the future)
- **New and existing users** — Works for developers who just installed VS Code and for those already using it with Copilot, extensions, etc.
- **Python MCP server** — Remote server built with Python (FastAPI + confluent-kafka), deployed on Kubernetes

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
│  │  │  MCP Auth Extension (ONE extension for all MCP servers)     │ │ │
│  │  │  • PingFederate SSO sign-in (same as Copilot sign-in)      │ │ │
│  │  │  • JWT token stored in OS keychain                         │ │ │
│  │  │  • Silent token refresh (background, every 50 min)         │ │ │
│  │  │  • Injects Authorization header into ALL MCP requests      │ │ │
│  │  └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                     │ │
│  │  ┌──────────────────────────────────────────────────────────────┐ │ │
│  │  │  .vscode/mcp.json (auto-configured by extension)            │ │ │
│  │  │  • mcp.company.com/kafka   → Kafka MCP server               │ │ │
│  │  │  • mcp.company.com/database → Database MCP server           │ │ │
│  │  │  • mcp.company.com/redis   → Redis MCP server               │ │ │
│  │  │  • Same JWT token for ALL servers                           │ │ │
│  │  └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                     │ │
│  │  GitHub Copilot (Claude) ← Developer talks here                   │ │
│  └─────────────────────────┬──────────────────────────────────────────┘ │
└────────────────────────────┼────────────────────────────────────────────┘
                             │
                             │ MCP Transport: Streamable HTTP (or SSE)
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

  Once the SSE/HTTP connection reaches the MCP server, everything
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
  "kafka-prod": { "type": "sse", "url": "https://mcp.company.com/kafka/prod" }
        │
        ▼ POST https://mcp.company.com/kafka/prod  (or over existing SSE)
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
| **Protocol** | REST API | JSON-RPC over SSE or Streamable HTTP |
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

The MCP SDK (Python `mcp` library or Java Spring AI MCP) handles this automatically — it exposes one HTTP/SSE endpoint and dispatches internally by the JSON-RPC `method` and tool `name`. The pod just listens on one port (e.g., `8000`) at `/`.

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
│ STEP 1: First-Time Sign-In (One-time, identical to Copilot)         │
│                                                                      │
│  Works for BOTH:                                                     │
│   • New VS Code users (fresh install)                                │
│   • Existing VS Code users (already have Copilot, extensions, etc.) │
└──────────────────────────────────────────────────────────────────────┘

  1. Developer installs the MCP Auth extension from VS Code Marketplace
     (or company distributes via VS Code Extension Pack)

  2. First time the extension activates:
     VS Code shows notification: "Sign in to Company Kafka MCP"
     ┌─────────────────────────────────────────────┐
     │ 🔑 MCP Kafka: Sign in to access clusters    │
     │                                             │
     │  [Sign In]    [Later]                       │
     └─────────────────────────────────────────────┘

  3. Developer clicks "Sign In":
     → Default browser opens (Chrome/Safari/Firefox)
     → PingFederate SSO login page (same page used for Jira, Confluence, etc.)
     → Developer enters company credentials (same login they use daily)
     → PingFederate authenticates and issues OAuth tokens

  4. Browser shows: "Authentication successful. You can close this window."

  5. VS Code extension receives tokens via OAuth callback:
     - Access Token (JWT, 1 hour TTL)
     - Refresh Token (30 days TTL)
     - Tokens stored in VS Code Secret Storage (macOS Keychain / Windows Credential Manager)

  6. VS Code shows: "✅ Connected to Kafka MCP. You have access to 3 clusters."

  7. DONE. Developer never sees this login again.

┌──────────────────────────────────────────────────────────────────────┐
│ STEP 2: Every MCP Request (Automatic, Developer Sees Nothing)       │
└──────────────────────────────────────────────────────────────────────┘

  Developer types in Copilot Chat: "List Kafka topics on dev"

  Behind the scenes (invisible to developer):

  VS Code Extension:
    1. Reads JWT token from VS Code Secret Storage
    2. Attaches to MCP request:
       - Authorization: Bearer <JWT_TOKEN>
    3. Sends HTTPS request to: https://mcp.company.com

  What the JWT token proves:
    - "I am rgr@company.com" (identity)
    - "I belong to data-platform team" (team membership)
    - "I have access to dev, staging clusters" (permissions)

  What the JWT token does NOT contain:
    - No Kafka passwords
    - No MSK credentials
    - No secrets of any kind

  The MCP server has its OWN credentials to connect to Kafka.
  The developer never sees or needs Kafka credentials.

  Request Flow:
    VS Code → HTTPS (TLS 1.3) → API Gateway → MCP Server → Kafka Cluster

  MCP Server:
    1. Receives JWT from request header
    2. Validates signature (was it signed by PingFederate? Not tampered?)
    3. Extracts user identity and permissions
    4. Executes Kafka operation
    5. Returns results to VS Code

┌──────────────────────────────────────────────────────────────────────┐
│ STEP 3: Token Refresh (Automatic, Silent — Same as Copilot)         │
└──────────────────────────────────────────────────────────────────────┘

  VS Code Extension (runs silently in background, every 50 minutes):
    1. Checks token expiry time
    2. If token expires in < 10 minutes:
       - Sends refresh request to PingFederate (using refresh token)
       - Receives new access token
       - Updates VS Code Secret Storage
    3. Developer is never interrupted ✅
    4. If refresh token expires (after 30 days of inactivity):
       - VS Code shows notification: "Session expired. Please sign in again."
       - Developer clicks "Sign In" → browser → SSO → done (30 seconds)
```

### Side-by-Side: GitHub Copilot vs MCP Kafka Auth

| Aspect | GitHub Copilot (today) | MCP Kafka (our approach) |
|--------|----------------------|---------------------------|
| **Install** | Copilot extension from Marketplace | MCP Auth extension from Marketplace |
| **First sign-in** | "Sign in to GitHub" → browser | "Sign in to Company SSO" → browser |
| **SSO Provider** | GitHub OAuth | PingFederate OAuth 2.0 |
| **Token storage** | VS Code Secret Storage (OS keychain) | VS Code Secret Storage (OS keychain) |
| **Token sent** | Every request → GitHub API | Every request → MCP Server |
| **Token refresh** | Silent, automatic | Silent, automatic |
| **Developer sees auth?** | Never (after first sign-in) | Never (after first sign-in) |
| **Session expired?** | "Sign in" notification | "Sign in" notification |

### New Users vs Existing Users

| Scenario | What Happens |
|----------|-------------|
| **New VS Code user** (fresh install) | Install VS Code → Install MCP Auth extension → Sign in once → Done |
| **Existing VS Code user** (already has Copilot, etc.) | Install MCP Auth extension → Sign in once → Done. All existing extensions/settings untouched |
| **User switching laptops** | Install MCP Auth extension on new laptop → Sign in once → Done |
| **User returning after 30 days** | VS Code prompts "Sign in again" → Click → browser → SSO → 30 seconds |

### JWT Token Structure (Issued by PingFederate)

```json
{
  "iss": "https://sso.company.com/pingfederate",
  "sub": "user@company.com",
  "aud": "mcp-api",
  "exp": 1707750000,
  "iat": 1707746400,
  "email": "user@company.com",
  "teams": ["data-platform", "backend-team"],
  "roles": ["developer"],
  "mcp_services": ["kafka", "database", "redis"]
}
```

**Note:** The `aud` (audience) is `mcp-api` — a single audience for ALL MCP servers. The gateway validates the token once, then routes to the correct server. PingFederate populates teams/roles from the corporate directory (Active Directory / LDAP). No manual role management needed.

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
# STATEFULSET: Kafka MCP Server (Per Cluster)
# =========================================
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka-mcp-dev-us-east-1
  namespace: mcp-kafka-dev
spec:
  serviceName: kafka-mcp-dev
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
      containers:
      - name: kafka-mcp
        image: company-ecr.amazonaws.com/kafka-mcp-python:v2.1.0
        ports:
        - containerPort: 8000
          name: mcp-api
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
    kind: StatefulSet
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
    
    # 5. Route to backend MCP pod
    backend_url = f"http://kafka-mcp-{cluster_id}-service.{cluster['mcp_namespace']}.svc.cluster.local:8000"
    
    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(
                f"{backend_url}/tools/call",
                json=request,
                headers={
                    "X-User-Id": user_info['user_id'],
                    "X-User-Email": user_info['email'],
                    "X-Cluster-Id": cluster_id
                },
                timeout=30.0
            )
            
            # 6. Audit log
            await log_operation(
                user_id=user_info['user_id'],
                cluster_id=cluster_id,
                operation=tool_name,
                params=request.get('arguments', {}),
                success=response.status_code == 200
            )
            
            return response.json()
            
        except httpx.TimeoutException:
            raise HTTPException(status_code=504, detail="Backend timeout")
        except Exception as e:
            logger.error(f"Backend error: {e}")
            raise HTTPException(status_code=502, detail="Backend error")
```

---

## 👥 Persona-Based Views

### 🎨 Architect Concerns

**Questions & Answers:**

**Q1: How do we ensure HA and disaster recovery?**
- Multi-AZ EKS cluster (3 availability zones)
- StatefulSet replicas=2 minimum per cluster
- ALB health checks with automatic pod replacement
- RTO: <5 minutes, RPO: 0 (no data loss, Kafka is source of truth)
- Backup strategy: Configuration in Git, audit logs in S3

**Q2: How do we handle burst traffic (Black Friday scenario)?**
- HPA: auto-scale from 2→10 replicas based on CPU/requests
- Kong rate limiting protects backend (100 req/min per user)
- Redis caching for list_topics (30s TTL)
- CloudFront caching for static responses
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
- CloudFront caching reduces backend calls (20% cost reduction)

**Q5: How to handle regulatory compliance (SOC2, GDPR)?**
- All operations logged with full audit trail
- GDPR flag on EU clusters (data residency enforcement)
- Encryption at rest (EBS volumes encrypted)
- Encryption in transit (TLS 1.3)
- Data retention policies enforced (90 days)
- Right to be forgotten (delete user audit logs)

---

### 💻 Developer Concerns

**Questions & Answers:**

**Q1: How do I get started? (Onboarding)**
```bash
# Step 1: Install CLI (5 minutes)
$ npm install -g @company/mcp-setup

# Step 2: Authenticate (1 minute)
$ mcp-setup init
# Opens browser → SSO login → Done!

# Step 3: VS Code auto-configured ✅
# Open Copilot Chat and type:
> List Kafka clusters I can access

# Done! Start working immediately.
```

**Q2: Which clusters can I access?**
- Ask Claude in VS Code: "What Kafka clusters do I have access to?"
- Or visit: https://mcp-portal.company.com/my-access
- Access is based on your team membership and role
- Typically: All devs get dev + staging, prod requires approval

**Q3: I need production access, how?**
- Submit ticket in self-service portal: https://mcp-portal.company.com/request-access
- Select cluster(s) you need
- Justify business need
- Manager approval required (automatic email)
- Access granted in 1 business day
- Time-limited (review every 90 days)

**Q4: I'm getting rate limited, why?**
- Dev: 100 req/min per user (very generous)
- Staging: 50 req/min
- Prod: 30 req/min (protection against accidents)
- If you hit limit: Wait 1 minute, then retry
- If you need more: Create ticket explaining use case

**Q5: Can I create topics in production?**
- Regular developers: Read-only access (list, describe)
- Kafka admins: Can create/delete with approval ticket
- Destructive operations require JIRA/ServiceNow ticket
- Approval workflow: Submit ticket → Manager approves → Operation allowed

**Q6: My token expired, what do I do?**
- VS Code extension auto-refreshes tokens every hour
- If manual refresh needed: Close VS Code, run `mcp-setup refresh`
- If still issues: `mcp-setup logout && mcp-setup init`

---

### ⚙️ Kafka Administrator Concerns

**Questions & Answers:**

**Q1: How do I add a new Kafka cluster?**
```yaml
# 1. Update ConfigMap
$ kubectl edit configmap kafka-clusters -n mcp-system

# 2. Add cluster entry (copy template from existing)
# Set: id, name, environment, bootstrap_servers, allowed_operations

# 3. Deploy new MCP StatefulSet
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
- If issues: Check pod logs `kubectl logs -f kafka-mcp-prod-us-east-1-0`
- Restart pod if needed: `kubectl rollout restart statefulset kafka-mcp-prod-us-east-1`

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
         https://mcp-portal.company.com/setup"
        ↓
09:15 - Developer clicks link → Portal page shows:
        
        ┌────────────────────────────────────────────┐
        │  🚀 MCP Setup Guide                        │
        │                                            │
        │  1. Install CLI:                           │
        │     $ npm install -g @company/mcp-setup    │
        │                                            │
        │  2. Authenticate:                          │
        │     $ mcp-setup init                       │
        │     [Opens browser for SSO login]          │
        │                                            │
        │  3. Done! VS Code is now configured.       │
        │                                            │
        │  Try it:                                   │
        │  - Open VS Code                            │
        │  - Open Copilot Chat (Cmd+Shift+I)        │
        │  - Type: "List Kafka topics in dev"       │
        └────────────────────────────────────────────┘
        ↓
09:25 - Developer runs: $ mcp-setup init
        ↓ (CLI opens browser)
        ↓
09:26 - Developer logs in with company SSO
        ↓
09:27 - CLI receives OAuth tokens
        CLI configures VS Code automatically:
        ✅ Creates ~/.vscode/mcp.json
        ✅ Stores tokens in OS keychain
        ✅ Installs VS Code MCP extension
        ↓
09:30 - Developer opens VS Code
        Opens Copilot Chat: "What Kafka clusters can I access?"
        
        Claude responds:
        "You have access to 2 Kafka clusters:
         1. Development (US East) - dev-us-east-1
         2. Staging (US East) - staging-us-east-1
         
         You can list topics, create topics, and delete topics in both.
         Would you like me to list topics from one of them?"
        ↓
        Developer: "List topics from dev"
        ↓
        Claude: [Calls MCP tool] → Shows 47 topics
        ↓
09:35 - Developer productive! ✅

Total onboarding time: 10 minutes
Manual intervention: ZERO ✅
```

---

## 🔍 Architecture Decision Records (ADRs)

### ADR-001: Why Python for MCP Server (not Java)?

**Decision:** Use Python with confluent-kafka-python for Kafka MCP servers

**Context:** 
- Need to build remote MCP servers for Kafka administration
- Must support SSE transport (Server-Sent Events)
- Target: 100-1000 concurrent users

**Rationale:**
- ✅ Official MCP SDK (Anthropic maintains Python SDK)
- ✅ 65% less code than Java (90 lines vs 300 lines)
- ✅ Faster development iterations (no compile step)
- ✅ Better ecosystem for SSE/WebSocket (FastAPI, Starlette)
- ✅ confluent-kafka-python has 95% performance of Java (uses librdkafka C library)
- ✅ Easier to hire Python developers for maintenance
- ✅ Faster startup (0.5s vs Java's 2-3s) - better for auto-scaling

**Trade-offs:**
- ❌ Slightly lower throughput than pure Java (90K vs 100K ops/s)
- ✅ But admin operations are <100 ops/s, so difference is negligible

**Status:** Approved

---

### ADR-002: Why StatefulSet instead of Deployment?

**Decision:** Each Kafka cluster = separate StatefulSet (not Deployment)

**Context:**
- Need to deploy Kafka MCP servers in Kubernetes
- Each server connects to one MSK cluster
- Require HA with multiple replicas

**Rationale:**
- ✅ Stable network identity (kafka-mcp-dev-0, kafka-mcp-dev-1)
- ✅ Sticky sessions possible if needed for connection pooling
- ✅ Easier audit trail (know which pod handled which request)
- ✅ Can attach persistent volumes if caching layer added later
- ✅ Graceful shutdown handling (drain connections before termination)

**Alternatives Considered:**
- Deployment: Simpler but no stable identity
- DaemonSet: One pod per node (overkill for our use case)

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
- ✅ WebSocket/SSE support (AWS API Gateway has limitations)
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

### ADR-004: MCP Transport Protocol — Streamable HTTP (and SSE)

**Decision:** Use **Streamable HTTP** as the primary MCP transport, with SSE backward compatibility

**Context:**
The MCP protocol defines how VS Code communicates with remote MCP servers. There are three transport options:

| Transport | Use Case | How It Works |
|-----------|----------|-------------|
| **stdio** | Local MCP servers only | MCP server runs as a subprocess on the developer's laptop. VS Code launches the process and communicates via stdin/stdout. Not applicable for remote/cloud servers. |
| **SSE** (Server-Sent Events) | Remote MCP servers (legacy) | The original remote transport. VS Code opens a persistent HTTP connection (SSE) to receive server-pushed events. Client-to-server messages sent via separate HTTP POST requests. |
| **Streamable HTTP** | Remote MCP servers (current) | The newer transport (MCP spec 2025+). Uses standard HTTP POST for all messages. Supports optional streaming via SSE when the server needs to push data. Simpler, more gateway-friendly. |

**How each transport works at the protocol level:**

```
STDIO (local only — NOT our use case):
  VS Code ──stdin──→ MCP server process (on same machine)
  VS Code ←─stdout── MCP server process
  Limitation: server must run on developer's laptop

SSE (remote — legacy transport):
  1. VS Code opens GET request → keeps connection open (SSE stream)
  2. Server sends events down the SSE stream (tool results, notifications)
  3. VS Code sends tool calls via separate POST requests to /message endpoint
  4. Server responses arrive on the SSE stream
  Flow: VS Code ──GET (SSE stream)──→ MCP Server (persistent connection)
        VS Code ──POST /message────→ MCP Server (tool call)
        VS Code ←──SSE event────── MCP Server (result)

Streamable HTTP (remote — current transport):
  1. VS Code sends POST request to MCP server endpoint
  2. Server can respond with:
     a. Regular JSON response (simple request/response)
     b. SSE stream (for long-running operations, streaming results)
  3. No persistent connection required (but supported for streaming)
  Flow: VS Code ──POST──→ MCP Server ──JSON response──→ VS Code
    or: VS Code ──POST──→ MCP Server ──SSE stream──→ VS Code (streaming)
```

**What this means for our architecture:**

```
Developer's Laptop                        AWS Cloud
┌──────────────┐                          ┌──────────────────┐
│ VS Code      │    Streamable HTTP       │ API Gateway       │
│              │──POST (tool call)───────→│ validates JWT     │
│ mcp.json     │      HTTPS + JWT         │                  │
│ tells VS Code│                          │ routes to MCP    │
│ to use       │←─JSON or SSE stream─────│ server           │
│ "type":"sse" │     (tool result)        │                  │
│ or "http"    │                          └───────┬──────────┘
└──────────────┘                                  │
                                           ┌──────▼──────────┐
                                           │ Kafka MCP Server │
                                           │ (no auth code)   │
                                           └─────────────────┘
```

**mcp.json server type field:**

  In mcp.json, the "type" field tells VS Code which transport to use:
  - `"type": "sse"` → SSE transport (widely supported, proven)
  - `"type": "http"` → Streamable HTTP transport (newer, simpler)
  - `"type": "stdio"` → Local subprocess (not for remote servers)

  Our extension generates mcp.json with the appropriate type.
  For maximum compatibility with current VS Code versions, we start with "sse".
  When Streamable HTTP is fully stable across all VS Code versions, switch to "http".

**Why SSE works well for our use case (and why we start with it):**
- ✅ Supported in all VS Code versions with MCP support (1.96+)
- ✅ Standard HTTP — works through corporate proxies and firewalls
- ✅ API Gateway (Kong/Nginx/ALB) handles SSE natively
- ✅ Automatic reconnection on network interruption
- ✅ JWT token sent as standard HTTP header (Authorization: Bearer)
- ✅ No WebSocket upgrade needed (simpler for enterprise networks)

**Why Streamable HTTP is the future:**
- ✅ Simpler — standard POST requests, no persistent connection required
- ✅ Better for serverless/Lambda deployments (no long-lived connections)
- ✅ Load balancers handle POST requests more predictably than SSE
- ✅ Optional streaming when needed (SSE response body)
- ✅ Becoming the default in MCP SDK 2025+

**Migration path:** Start with `"type": "sse"` → switch to `"type": "http"` via
a single extension settings update pushed to all 1,000 developers. Zero re-auth.

**Status:** Approved (SSE as initial transport, Streamable HTTP as upgrade path)

---

## 📊 Estimated Costs (AWS Infrastructure)

### Monthly Cost Breakdown (100 developers, 5 Kafka clusters)

| Component | Specification | Quantity | Unit Cost | Monthly Cost |
|-----------|--------------|----------|-----------|--------------|
| **EKS Control Plane** | Standard | 1 | $73/month | $73 |
| **EC2 Instances (On-Demand)** | m5.xlarge (prod) | 3 | $144/month | $432 |
| **EC2 Instances (Spot)** | m5.large (dev/staging) | 6 | $32/month | $194 |
| **Application Load Balancer** | 1x ALB | 1 | $25/month | $25 |
| **CloudFront** | ~1TB data transfer | - | $85/TB | $85 |
| **RDS PostgreSQL** | db.t3.medium (audit logs) | 1 | $65/month | $65 |
| **ElastiCache Redis** | cache.t3.micro (rate limit) | 1 | $15/month | $15 |
| **CloudWatch Logs** | 50GB/month retention | - | $0.50/GB | $25 |
| **Secrets Manager** | 10 secrets | 10 | $0.40/secret | $4 |
| **Route53** | Hosted zone | 1 | $0.50/month | $0.50 |
| **VPC Endpoints** | S3, ECR, Secrets Manager | 3 | $7/month | $21 |
| **AWS MSK** | *Already exists* | - | - | $0 (not counted) |
| | | | **Total** | **~$940/month** |

**Per developer cost:** $9.40/month per developer ✅

**Scaling Projections:**

| Users | Monthly Cost | Per User | Notes |
|-------|--------------|----------|-------|
| 100 | $940 | $9.40 | Current estimate |
| 500 | $1,850 | $3.70 | Economy of scale (same infra, more users) |
| 1000 | $2,400 | $2.40 | Add 3 more nodes, scale horizontally |

**Cost Optimizations Applied:**
- ✅ Spot instances for dev/staging (50% savings)
- ✅ Right-sized pods (512MB instead of 1GB)
- ✅ CloudFront caching (reduces backend load)
- ✅ Auto-scaling down nights/weekends (30% savings)

---

## 🚀 Implementation Roadmap

### Phase 1: Foundation (Weeks 1-4)

**Week 1-2: Infrastructure Setup**
- [ ] Provision EKS cluster in AWS
- [ ] Setup VPC with private subnets for backend access
- [ ] Deploy API Gateway (Kong / Nginx)
- [ ] Configure ALB
- [ ] Integrate with PingFederate (OAuth 2.0, JWKS endpoint for JWT validation)
- [ ] Setup audit logging

**Week 3-4: MCP Server + VS Code Extension Development**
- [ ] Build Python Kafka MCP server (FastAPI + confluent-kafka)
- [ ] Create Docker image + push to ECR
- [ ] Deploy to K8s
- [ ] Build VS Code auth extension (PingFederate SSO, token storage, auto-refresh)
- [ ] Publish extension to VS Code Marketplace (or internal distribution)
- [ ] Configure API Gateway routing (/kafka → Kafka MCP server)

**Deliverables:**
- ✅ Working Kafka MCP server for dev cluster
- ✅ PingFederate authentication working via VS Code extension
- ✅ VS Code can connect and list Kafka topics

---

### Phase 2: Multi-Cluster & Additional MCP Servers (Weeks 5-8)

**Week 5-6: Multi-Cluster Kafka + Hardening**
- [ ] Deploy Kafka MCP pods for staging + prod clusters
- [ ] Configure per-cluster credentials (K8s Secrets)
- [ ] Setup monitoring (Prometheus + Grafana)
- [ ] Enable audit logging
- [ ] Rate limiting via API Gateway

**Week 7-8: Additional MCP Servers (Database, Redis, etc.)**
- [ ] Build Database MCP server (pure DB logic, no auth code)
- [ ] Build Redis MCP server (pure Redis logic, no auth code)
- [ ] Deploy behind same API Gateway
- [ ] Add routes: /database → DB MCP, /redis → Redis MCP
- [ ] Update VS Code extension config to show new servers
- [ ] Developers get access with same token — no new sign-in needed

**Deliverables:**
- ✅ 3 Kafka clusters accessible (dev, staging, prod)
- ✅ Database + Redis MCP servers deployed
- ✅ All servers accessible with single SSO sign-in

---

### Phase 3: Production Rollout (Weeks 9-12)

**Week 9-10: Staging Rollout**
- [ ] Onboard 10 beta users
- [ ] Collect feedback
- [ ] Fix bugs
- [ ] Load testing (simulate 100 concurrent users)
- [ ] Tune auto-scaling settings

**Week 11-12: Production Rollout**
- [ ] Create runbooks (troubleshooting guides)
- [ ] Announce to company (all-hands, Slack)
- [ ] Onboard first 50 users
- [ ] Monitor metrics closely
- [ ] Gradual rollout to all developers

**Deliverables:**
- ✅ 100+ users onboarded
- ✅ SLA: 99.9% uptime
- ✅ All MCP servers accessible via single sign-in

---

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
- DR region (optional): _______________

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

**Budget:** ~$940/month for 100 users (scales to $2,400/month for 1000 users)

---

## 📝 Document Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-12 | Senior Architect | Initial architecture document |

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
  - [ ] Store tokens in `vscode.SecretStorage` (OS keychain)
  - [ ] Background token refresh (every ~50 min with random jitter)
  - [ ] Handle expired refresh tokens (prompt re-sign-in)
  - [ ] Clear all tokens on sign-out
- [ ] **2.4 mcp.json Auto-Management**
  - [ ] Generate `~/.vscode/mcp.json` with all server entries + JWT header
  - [ ] Merge with existing mcp.json (don't overwrite manual entries)
  - [ ] Rewrite mcp.json silently on token refresh
  - [ ] Regenerate mcp.json on VS Code restart (from keychain)
  - [ ] Self-heal on accidental deletion/corruption
- [ ] **2.5 Settings & Config Watcher**
  - [ ] `mcpAuth.gatewayUrl` setting
  - [ ] `mcpAuth.ssoUrl` setting
  - [ ] `mcpAuth.clientId` setting
  - [ ] `mcpAuth.servers` array (dynamic server list)
  - [ ] Watch for settings changes → auto-update mcp.json
- [ ] **2.6 UI**
  - [ ] Status bar item (connected/disconnected/reconnecting)
  - [ ] Sign-in notification with action button
  - [ ] Extension appears in VS Code Accounts menu
- [ ] **2.7 Testing**
  - [ ] First-time sign-in flow works
  - [ ] Token refresh works silently
  - [ ] Sign-out clears everything
  - [ ] New server added via settings → mcp.json updated, no re-auth
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
- [ ] **3.3 API Gateway (Kong / Nginx)**
  - [ ] Deploy Kong (or Nginx) in EKS
  - [ ] Configure JWT validation plugin (JWKS caching)
  - [ ] Configure rate limiting (per user)
  - [ ] Configure audit logging
  - [ ] TLS termination (certificate from ACM)
  - [ ] Route: `/kafka/dev` → `kafka-mcp-dev-service:8000`
  - [ ] Route: `/kafka/staging` → `kafka-mcp-staging-service:8000`
  - [ ] Route: `/kafka/prod` → `kafka-mcp-prod-service:8000`
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
  - [ ] SSE transport working (VS Code `"type": "sse"`)
  - [ ] Streamable HTTP support (VS Code `"type": "http"`) — future
- [ ] **4.4 Containerization**
  - [ ] Dockerfile (multi-stage build)
  - [ ] Health check endpoint (`/health`)
  - [ ] Non-root user in container
  - [ ] Image size optimized
- [ ] **4.5 Kubernetes Deployment**
  - [ ] Deployment manifest (or StatefulSet)
  - [ ] Service (ClusterIP, port 8000)
  - [ ] Resource limits (CPU, memory)
  - [ ] Liveness + readiness probes
  - [ ] HPA (Horizontal Pod Autoscaler)
- [ ] **4.6 Per-Cluster Deployments**
  - [ ] Kafka Dev MCP pod → connected to Dev MSK cluster
  - [ ] Kafka Staging MCP pod → connected to Staging MSK cluster
  - [ ] Kafka Prod MCP pod → connected to Prod MSK cluster
  - [ ] Kafka Prod EU MCP pod → connected to EU MSK cluster (if applicable)
  - [ ] Kafka Prod West MCP pod → connected to West MSK cluster (if applicable)
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
  - [ ] VS Code extension: PKCE generation, JWT parsing, token lifecycle
  - [ ] MCP server: each tool function tested individually
- [ ] **9.2 Integration Tests**
  - [ ] End-to-end: VS Code → Gateway → MCP Server → Kafka
  - [ ] Auth flow: sign-in → token → tool call → result
  - [ ] Token refresh: silent refresh updates mcp.json
  - [ ] New server addition: settings change → mcp.json update
- [ ] **9.3 Load Testing**
  - [ ] 100 concurrent users simulated
  - [ ] 1,000 concurrent users simulated (target scale)
  - [ ] Token refresh thundering herd test (jitter verified)
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
| 2. VS Code Extension | 8 main / 30 sub | 0 | 🔴 Not started |
| 3. AWS Infrastructure | 5 main / 18 sub | 0 | 🔴 Not started |
| 4. Kafka MCP Server | 7 main / 25 sub | 0 | 🔴 Not started |
| 5. Amazon MSK | 3 main / 11 sub | 0 | 🔴 Not started |
| 6. Monitoring | 3 main / 12 sub | 0 | 🔴 Not started |
| 7. Security | 4 main / 13 sub | 0 | 🔴 Not started |
| 8. Documentation | 4 main / 13 sub | 0 | 🔴 Not started |
| 9. Testing | 4 main / 14 sub | 0 | 🔴 Not started |
| 10. Rollout | 4 main / 13 sub | 0 | 🔴 Not started |
| **TOTAL** | **45 main / 162 sub** | **0** | 🔴 **Not started** |

Update this summary table as items are completed. Change status to:
- 🟡 In progress (some items done)
- 🟢 Complete (all items done)

---

**Questions or need clarification?** Add your questions in comments or create GitHub issues for discussion.
