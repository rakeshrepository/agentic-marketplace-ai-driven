# Prompt: Build Generic MCP Auth Extension for Enterprise

**Purpose:** Use this prompt with an AI coding agent (Claude, Copilot, Cursor, etc.) to build the VS Code extension.  
**Target:** 100–1,000 developers accessing Kafka clusters via MCP  
**Last Updated:** February 14, 2026

---

## Document Overview

This prompt provides everything needed to implement the VS Code authentication extension for remote MCP servers. It is aligned with the `REMOTE_MCP_ARCHITECTURE_GUIDE.md` decisions:

| Decision | Value |
|----------|-------|
| **Transport** | Streamable HTTP (`type: "http"`) |
| **Environments** | dev, sit, uat, prod |
| **Token Storage** | OS Keychain (persistence) + Memory cache (speed) |
| **Token Lifecycle** | Access: 1 hour, Refresh: 30 days |
| **Extension Pattern** | `vscode.AuthenticationProvider` (~150 lines) |
| **mcp.json** | URLs + Authorization header (auto-generated) |
| **Gateway** | Kong on EKS (validates JWT via cached JWKS) |

**Key Sections:**
1. **The Prompt** — Copy-paste into AI coding agent
2. **Expected Outcomes** — Success criteria for verification
3. **Developer Implementation Checklist** — Day-by-day implementation guide

---

## The Prompt

```
Build a VS Code extension called "Company MCP Auth" that provides centralized 
authentication for ALL company MCP servers. This is a GENERIC auth extension — 
it does NOT know about Kafka, databases, Redis, or any specific MCP server. 
It ONLY handles authentication and server discovery.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TARGET SCALE & DEPLOYMENT CONTEXT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Developers:       100 – 1,000 (enterprise engineering organization)
  Primary workload: 3 – 5 Kafka clusters (Amazon MSK) across multiple AWS regions
  Environments:     dev, sit, uat, prod
  Regions:          e.g., us-east-1, us-west-2, eu-west-1
  MCP servers:      Kafka MCP (primary), with future expansion to
                    Database, Redis, S3, Elasticsearch, etc.
  API Gateway:      Single gateway URL per environment
                    (or regional gateways with latency-based routing)
  Concurrency:      Up to 1,000 active VS Code sessions simultaneously

  The extension must handle this scale gracefully:
  - Token refresh STAGGERED across developers (don't hammer PingFederate
    with 1,000 refresh requests at the same second)
  - mcp.json updates are local-only (no server roundtrip per developer)
  - Gateway URL supports regional routing if needed
  - Multiple Kafka clusters appear as separate MCP server entries
    (e.g., kafka-dev, kafka-sit, kafka-uat, kafka-prod)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

The extension handles:
  1. PingFederate SSO authentication (OAuth 2.0 + PKCE)
  2. JWT token storage in OS keychain
  3. Silent background token refresh
  4. Auto-generation of .vscode/mcp.json for all registered MCP servers
  5. Dynamic server discovery (admin adds new MCP server → all developers 
     get access immediately with zero re-authentication)

The developer experience must be IDENTICAL to GitHub Copilot's sign-in:
  - First time: VS Code notification "Sign in to Company MCP" → click → 
    browser opens → PingFederate SSO → done forever
  - Every subsequent launch: tokens loaded from keychain, fully silent
  - Token refresh: automatic, background, developer never sees it
  - Session expired (30 days inactive): one-click re-auth (30 seconds)

This extension serves TWO user types:
  - New VS Code users (fresh install) — install extension, sign in, done
  - Existing VS Code users (already have Copilot, etc.) — install extension,
    sign in, done. Zero impact on existing setup.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ARCHITECTURE PRINCIPLE: CENTRALIZED AUTH
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Auth code lives in exactly TWO places in the entire system:

  1. THIS VS Code extension — gets the JWT token from PingFederate
  2. API Gateway (server-side) — validates the JWT token

MCP servers (Kafka, Database, Redis, S3, etc.) have ZERO auth code.
They sit behind the API Gateway and receive pre-authenticated requests.

This means:
  ✅ ONE extension for ALL MCP servers (current and future)
  ✅ ONE sign-in for ALL MCP servers
  ✅ ONE token for ALL MCP servers
  ✅ Adding a new MCP server = add URL path in settings, NO re-auth
  ✅ MCP server developers never write auth code

Flow:
  VS Code Extension (gets JWT)
    → sends JWT with every MCP request (Authorization: Bearer <token>)
      → API Gateway receives request
        → validates JWT LOCALLY (see JWT VALIDATION section below)
        → extracts user identity from JWT claims (email, teams, roles)
        → adds X-User-Email, X-User-Teams headers to downstream request
        → routes to correct MCP server (/kafka/dev, /kafka/prod, etc.)
          → MCP server receives pre-authenticated request with user context
            → executes business logic (NO auth code, NO call to PingFederate)
              → returns result to VS Code

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
JWT VALIDATION: HOW THE API GATEWAY VALIDATES TOKENS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

CRITICAL: Neither the API Gateway NOR the MCP servers call PingFederate 
on every request. JWT validation is LOCAL and OFFLINE.

How JWT validation works (zero network calls per request):

  1. PingFederate signs JWTs with a private key (RS256 / RSA)
  2. PingFederate publishes the matching PUBLIC keys at a JWKS endpoint:
     https://sso.company.com/pf/JWKS
  3. API Gateway fetches these public keys ONCE on startup, then
     caches them (refreshes every 1-24 hours, or on key rotation)
  4. For EVERY incoming request, the gateway:
     a. Reads the Authorization header → extracts the JWT
     b. Parses the JWT header → finds the "kid" (key ID)
     c. Looks up the matching public key from cached JWKS
     d. Verifies the signature mathematically (RSA verify — CPU only, 
        no network call, takes <1ms)
     e. Checks: token not expired (exp claim)
     f. Checks: audience is "mcp-api" (aud claim)
     g. Checks: issuer is PingFederate (iss claim)
     h. If ALL checks pass → request is AUTHENTICATED
     i. Extracts claims (email, teams, roles) → forwards to MCP server

  This is EXACTLY how every enterprise API gateway works:
  - Kong:   built-in JWT plugin (jwt-validator)
  - Nginx:  ngx_http_auth_jwt_module (Nginx Plus) or lua-resty-jwt
  - AWS ALB: built-in OIDC authentication action
  - Envoy:  jwt_authn HTTP filter
  - Istio:  RequestAuthentication + AuthorizationPolicy

Why this is correct for 1,000 developers:

  ┌─────────────────────────────────────────────────────────────────┐
  │ WRONG approach (DO NOT DO THIS):                                │
  │                                                                 │
  │   Every request → MCP server calls PingFederate to validate     │
  │   1,000 developers × 10 requests/min = 10,000 calls/min to SSO │
  │   → PingFederate becomes bottleneck, single point of failure    │
  │   → Adds 50-200ms latency per request (network round trip)      │
  │   → If PingFederate is down, ALL MCP servers stop working       │
  └─────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────┐
  │ CORRECT approach (what we do):                                  │
  │                                                                 │
  │   Gateway caches JWKS public keys (fetches once, refreshes      │
  │   every few hours). Validates JWT locally using cached keys.     │
  │   → ZERO calls to PingFederate per request                      │
  │   → <1ms validation overhead (CPU-only RSA signature verify)    │
  │   → If PingFederate is down: existing tokens still validate!    │
  │   → Only NEW sign-ins fail (refresh works until keys rotate)    │
  │   → Scales to 100,000 requests/min with zero SSO load           │
  └─────────────────────────────────────────────────────────────────┘

What the MCP server receives (example downstream request):

  Original request from VS Code:
    GET /kafka/prod/tools/list-topics
    Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
  
  After API Gateway validation → stripped and forwarded to MCP server:
    GET /tools/list-topics
    X-User-Email: developer@company.com
    X-User-Teams: ["platform-team", "data-team"]
    X-User-Roles: ["developer"]
    X-Request-ID: abc-123-def
    (Authorization header REMOVED — MCP server never sees the JWT)

  MCP server code (Python example):
    @app.route('/tools/list-topics')
    def list_topics(request):
        user_email = request.headers['X-User-Email']  # Already authenticated
        user_teams = json.loads(request.headers['X-User-Teams'])
        
        # Pure business logic — no auth code anywhere
        topics = kafka_admin.list_topics(cluster='prod')
        
        # Optional: team-based filtering (authorization, not authentication)
        if 'admin-team' not in user_teams:
            topics = [t for t in topics if not t.startswith('_')]
        
        return topics

JWKS caching in the API Gateway (example Kong configuration):

  plugins:
    - name: jwt
      config:
        uri_param_names: []
        header_names: ["Authorization"]
        claims_to_verify: ["exp", "aud"]
        key_claim_name: "kid"
        # Kong caches JWKS automatically and refreshes on key rotation
        # No per-request call to PingFederate

  # Or for Nginx (lua-resty-jwt):
  local jwt = require "resty.jwt"
  local validators = require "resty.jwt-validators"
  
  -- JWKS cached in shared memory (fetched every 12 hours)
  local jwt_obj = jwt:verify(cached_public_key, token, {
      iss = "https://sso.company.com",
      aud = "mcp-api",
      exp = validators.is_not_expired()
  })
  -- Takes <1ms, zero network calls

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EXTENSION IDENTITY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Name:             mcp-auth
Display Name:     Company MCP Auth
Description:      Centralized SSO authentication for all company MCP servers (100–1,000 developers, multi-region Kafka)
Publisher:        company-name
Category:         Other
Activation:       onStartupFinished (activate when VS Code starts)
Icon:             Company logo or lock icon
License:          Internal / Proprietary

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PINGFEDERATE OAUTH 2.0 CONFIGURATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

These values are configurable via extension settings (defaults shown):

PingFederate Server:       https://sso.company.com
Authorization Endpoint:    https://sso.company.com/as/authorization.oauth2
Token Endpoint:            https://sso.company.com/as/token.oauth2
UserInfo Endpoint:         https://sso.company.com/idp/userinfo.openid
JWKS URI:                  https://sso.company.com/pf/JWKS

Client ID:                 mcp-vscode
Client Secret:             NONE (public client — VS Code cannot store secrets)
Redirect URI:              http://localhost:{dynamic_port}/callback
Grant Type:                Authorization Code + PKCE
Response Type:             code

PKCE (Proof Key for Code Exchange):
  - REQUIRED for all public clients (VS Code = public client)
  - code_verifier:  random 43-128 character string (URL-safe base64)
  - code_challenge: Base64URL(SHA256(code_verifier))
  - code_challenge_method: S256

Scopes:                    openid profile email mcp-access

Token TTLs (set in PingFederate, extension must handle):
  - Access Token:   1 hour (JWT, signed by PingFederate)
  - Refresh Token:  30 days (opaque string)
  - ID Token:       1 hour (JWT, contains user info)

JWT Audience:              mcp-api  (single audience for ALL MCP servers)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VS CODE APIs TO USE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. vscode.authentication.registerAuthenticationProvider()
   - Provider ID: "company-sso"
   - Provider Label: "Company SSO (PingFederate)"
   - This makes the extension show in VS Code's Accounts menu
     (same place where "GitHub" and "Microsoft" appear — bottom-left 
     avatar icon)
   - Implement: AuthenticationProvider interface
     - getSessions(): return session from SecretStorage
     - createSession(scopes): trigger OAuth PKCE flow
     - removeSession(sessionId): clear tokens

2. vscode.SecretStorage (context.secrets)
   KEY                      VALUE                  PURPOSE
   mcp-access-token         JWT string             Auth header for requests
   mcp-refresh-token        Opaque string          Refresh expired access token
   mcp-token-expiry         Timestamp (ms)         Know when to refresh
   mcp-user-email           user@company.com       Status bar display
   mcp-user-teams           JSON array             Team membership

   SecretStorage uses the OS keychain:
   - macOS: Keychain Access
   - Windows: Credential Manager
   - Linux: libsecret / GNOME Keyring

   ┌─────────────────────────────────────────────────────────────────────┐
   │  TWO-TIER TOKEN STORAGE PATTERN (CRITICAL)                        │
   │                                                                     │
   │  ❌ WRONG: Read from keychain on every getSessions() call          │
   │     → Slow (disk I/O), may prompt for keychain password            │
   │                                                                     │
   │  ✅ CORRECT: Two-tier caching                                       │
   │                                                                     │
   │  Tier 1: OS Keychain (persistence)                                 │
   │     → Written on sign-in and token refresh                         │
   │     → Survives VS Code restart, machine reboot                     │
   │     → Read ONCE at extension activation                            │
   │                                                                     │
   │  Tier 2: Memory cache (speed)                                      │
   │     → All getSessions() calls read from memory (~0ms)              │
   │     → Updated on sign-in, refresh, sign-out                        │
   │     → Lost on VS Code restart (repopulated from keychain)          │
   │                                                                     │
   │  Flow:                                                              │
   │  1. Extension activates → read keychain → cache in memory          │
   │  2. getSessions() called → return from MEMORY (instant)            │
   │  3. Token refresh → update BOTH memory + keychain                  │
   │  4. Sign out → clear BOTH memory + keychain                        │
   │  5. VS Code restart → repeat step 1                                │
   └─────────────────────────────────────────────────────────────────────┘

3. vscode.env.openExternal(uri)
   - Opens default browser for SSO login
   - Same API that GitHub Copilot uses

4. vscode.window.showInformationMessage()
   - Sign-in notification with action buttons
   - Success/error messages

5. vscode.workspace.getConfiguration('mcpAuth')
   - Read gateway URL, SSO URL, client ID, server list

6. vscode.workspace.onDidChangeConfiguration
   - Watch for changes to mcpAuth.servers
   - Auto-regenerate mcp.json when admin adds new servers

7. vscode.StatusBarItem
   - Show connection status at bottom of VS Code window

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EXTENSION SETTINGS (package.json → contributes.configuration)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

"mcpAuth.gatewayUrl": {
  "type": "string",
  "default": "https://mcp.company.com",
  "description": "MCP API Gateway URL — single entry point for all MCP servers"
}

"mcpAuth.ssoUrl": {
  "type": "string",
  "default": "https://sso.company.com",
  "description": "PingFederate SSO base URL"
}

"mcpAuth.clientId": {
  "type": "string",
  "default": "mcp-vscode",
  "description": "OAuth 2.0 client ID registered in PingFederate (public client)"
}

"mcpAuth.servers": {
  "type": "array",
  "description": "MCP servers behind the gateway. All share the same auth token. Each Kafka cluster (dev, sit, uat, prod) is a separate entry. Add new entries here — developers get immediate access with zero re-auth.",
  "default": [
    { "name": "kafka-dev",  "path": "/kafka/dev",  "label": "Kafka Dev" },
    { "name": "kafka-sit",  "path": "/kafka/sit",  "label": "Kafka SIT" },
    { "name": "kafka-uat",  "path": "/kafka/uat",  "label": "Kafka UAT" },
    { "name": "kafka-prod", "path": "/kafka/prod", "label": "Kafka Prod" }
  ],
  "items": {
    "type": "object",
    "properties": {
      "name":  { "type": "string", "description": "Unique server ID (used in mcp.json)" },
      "path":  { "type": "string", "description": "URL path on gateway (e.g., /kafka)" },
      "label": { "type": "string", "description": "Human-readable name for UI" }
    },
    "required": ["name", "path", "label"]
  }
}

"mcpAuth.tokenRefreshIntervalMinutes": {
  "type": "number",
  "default": 50,
  "description": "How often to check token expiry (minutes). Token refreshed if expiring within 10 minutes."
}

"mcpAuth.mcpConfigScope": {
  "type": "string",
  "enum": ["user", "workspace"],
  "default": "user",
  "description": "Where to write mcp.json — user-level (global) or workspace-level"
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COMPLETE AUTH FLOW (STEP BY STEP — IMPLEMENT EXACTLY)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

STEP 1: Extension activates (onStartupFinished)
─────────────────────────────────────────────────
  → context.secrets.get('mcp-access-token')
  → context.secrets.get('mcp-token-expiry')

  IF tokens exist AND not expired:
    → Parse JWT to get user email
    → Update status bar: "🟢 MCP: user@company.com"
    → Generate/verify mcp.json is up to date
    → Start background refresh timer
    → DONE (developer sees nothing — fully silent)

  IF tokens exist AND expired:
    → Try refresh using stored refresh token (STEP 7)
    → If refresh succeeds: same as above
    → If refresh fails: go to STEP 2

  IF no tokens exist:
    → Show notification:
      ┌─────────────────────────────────────────────────────┐
      │ 🔑 Company MCP: Sign in to access MCP servers      │
      │                                                     │
      │  [Sign In]    [Later]                               │
      └─────────────────────────────────────────────────────┘
    → Update status bar: "🔴 MCP: Sign in required"
    → Wait for user click

STEP 2: User clicks "Sign In" (or runs command "MCP Auth: Sign In")
────────────────────────────────────────────────────────────────────
  → Generate PKCE values:
    const codeVerifier = base64url(crypto.randomBytes(32))
    // 43 chars, URL-safe
    
    const codeChallenge = base64url(sha256(codeVerifier))
    // SHA-256 hash, then base64url encode

  → Generate state parameter:
    const state = crypto.randomBytes(16).toString('hex')
    // 32-char hex string for CSRF protection

  → Find available port:
    const server = http.createServer()
    server.listen(0)  // OS assigns random available port
    const port = server.address().port

  → Build authorization URL:
    const authUrl = new URL('https://sso.company.com/as/authorization.oauth2')
    authUrl.searchParams.set('client_id', 'mcp-vscode')
    authUrl.searchParams.set('response_type', 'code')
    authUrl.searchParams.set('redirect_uri', `http://localhost:${port}/callback`)
    authUrl.searchParams.set('scope', 'openid profile email mcp-access')
    authUrl.searchParams.set('state', state)
    authUrl.searchParams.set('code_challenge', codeChallenge)
    authUrl.searchParams.set('code_challenge_method', 'S256')

  → Open browser:
    await vscode.env.openExternal(vscode.Uri.parse(authUrl.toString()))

STEP 3: User logs in via PingFederate SSO in browser
──────────────────────────────────────────────────────
  → PingFederate shows company login page 
    (same page used for Jira, Confluence, email, etc.)
  → User enters company credentials
  → If MFA enabled: user completes MFA challenge
  → PingFederate redirects browser to:
    http://localhost:{port}/callback?code={AUTH_CODE}&state={STATE}

STEP 4: Extension receives OAuth callback on local HTTP server
──────────────────────────────────────────────────────────────
  → Parse query parameters: code and state
  
  → VERIFY state matches the one we generated:
    if (receivedState !== expectedState) {
      throw new Error('CSRF attack: state mismatch')
    }

  → Respond to browser with success HTML page:
    res.writeHead(200, { 'Content-Type': 'text/html' })
    res.end(`
      <html>
      <body style="font-family: -apple-system, sans-serif; text-align: center; padding: 60px;">
        <h1>✅ Authentication Successful</h1>
        <p>You can close this window and return to VS Code.</p>
        <script>setTimeout(() => window.close(), 3000)</script>
      </body>
      </html>
    `)

  → Close the HTTP server immediately:
    server.close()

STEP 5: Exchange authorization code for tokens
───────────────────────────────────────────────
  → POST to PingFederate token endpoint:

    POST https://sso.company.com/as/token.oauth2
    Content-Type: application/x-www-form-urlencoded

    Body (URL-encoded):
      grant_type=authorization_code
      &code={AUTH_CODE}
      &redirect_uri=http://localhost:{port}/callback
      &client_id=mcp-vscode
      &code_verifier={codeVerifier}

  → PingFederate responds with:
    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",    // JWT, 1 hour
      "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2...", // Opaque, 30 days
      "id_token": "eyJhbGciOiJSUzI1NiIs...",         // JWT, user info
      "token_type": "Bearer",
      "expires_in": 3600                              // seconds
    }

  → Store in VS Code SecretStorage:
    await context.secrets.store('mcp-access-token', response.access_token)
    await context.secrets.store('mcp-refresh-token', response.refresh_token)
    await context.secrets.store('mcp-token-expiry', 
      String(Date.now() + response.expires_in * 1000))
    
    // Parse ID token to get user info
    const idPayload = JSON.parse(atob(response.id_token.split('.')[1]))
    await context.secrets.store('mcp-user-email', idPayload.email)

STEP 6: Generate .vscode/mcp.json for all MCP servers
──────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────┐
  │  HOW .vscode/mcp.json WORKS — FULL LIFECYCLE                       │
  │                                                                     │
  │  mcp.json is VS Code's NATIVE configuration file for MCP servers.   │
  │  It is NOT our invention — it is part of VS Code's built-in MCP     │
  │  support (shipped since VS Code 1.96+). VS Code reads this file     │
  │  automatically and connects to every server listed in it.           │
  │                                                                     │
  │  Our extension's ONLY job: write/update this file with the correct  │
  │  server URLs and inject the current JWT token as an auth header.    │
  │  VS Code's built-in MCP client handles everything else.             │
  └─────────────────────────────────────────────────────────────────────┘

  WHO DOES WHAT:

  ┌──────────────────────┬────────────────────────────────────────────────┐
  │ Component            │ Responsibility                                 │
  ├──────────────────────┼────────────────────────────────────────────────┤
  │ Our auth extension   │ WRITES mcp.json (servers + JWT token)         │
  │ VS Code (built-in)   │ READS mcp.json, opens SSE connections         │
  │ Copilot Chat         │ DISCOVERS tools from connected MCP servers    │
  │ API Gateway          │ Receives SSE connection, validates JWT        │
  │ MCP Server           │ Responds with tool list + executes tool calls │
  └──────────────────────┴────────────────────────────────────────────────┘

  WHAT HAPPENS WHEN mcp.json IS WRITTEN (step by step):

  1. Our extension writes mcp.json with server entries + Authorization header
  2. VS Code detects the file change (built-in file watcher)
  3. For EACH server entry in mcp.json, VS Code's MCP client:
     a. Opens an SSE (Server-Sent Events) connection to the URL
        e.g., GET https://mcp.company.com/kafka/prod
        with header: Authorization: Bearer eyJhbGci...
     b. Sends MCP handshake (initialize request)
     c. MCP server responds with its capabilities + list of tools
        e.g., "list-topics", "describe-topic", "list-consumer-groups"
     d. VS Code registers these tools in Copilot Chat's tool palette
  4. Developer types in Copilot Chat: "List topics on kafka-prod"
  5. Copilot Chat matches the intent → calls the "list-topics" tool
  6. VS Code sends the tool call over the existing SSE connection
     (with the Authorization header from mcp.json automatically attached)
  7. API Gateway validates the JWT → forwards to MCP server
  8. MCP server executes → returns result → appears in Copilot Chat

  WHERE mcp.json LIVES:

  ┌──────────────────────┬────────────────────────────────────────────────┐
  │ Scope                │ File Location                                  │
  ├──────────────────────┼────────────────────────────────────────────────┤
  │ User-level (global)  │ ~/.vscode/mcp.json                            │
  │                      │ Applies to ALL workspaces/projects the         │
  │                      │ developer opens. Recommended for our use case. │
  ├──────────────────────┼────────────────────────────────────────────────┤
  │ Workspace-level      │ {project}/.vscode/mcp.json                    │
  │                      │ Applies only to that specific project.         │
  │                      │ Useful for project-specific MCP servers.       │
  └──────────────────────┴────────────────────────────────────────────────┘

  We default to USER-LEVEL because Kafka MCP servers are not project-specific.
  A developer working on ANY project needs access to the same Kafka clusters.

  LIFECYCLE EVENTS THAT TRIGGER mcp.json UPDATE:

  ┌──────────────────────────────────┬───────────────────────────────────┐
  │ Event                            │ What happens to mcp.json          │
  ├──────────────────────────────────┼───────────────────────────────────┤
  │ First sign-in                    │ Created with all servers + token  │
  │ Extension activates (VS Code     │ Verified/regenerated with current │
  │   start, after reboot)           │   token from keychain             │
  │ Token refreshed (every ~50 min)  │ Updated with new token            │
  │ Admin adds new server (settings  │ New entry added, same token       │
  │   sync / MDM push)              │   (no re-auth needed)             │
  │ Sign out                         │ Auth headers removed, server      │
  │                                  │   entries preserved               │
  │ Token expired (30 days inactive) │ Auth headers removed until re-    │
  │                                  │   sign-in                         │
  └──────────────────────────────────┴───────────────────────────────────┘

  DEVELOPER LAPTOP VIEW (what the developer's machine looks like):

  Developer's Laptop
  ┌────────────────────────────────────────────────────────────────────┐
  │                                                                    │
  │  VS Code                                                           │
  │  ┌──────────────────────────────────────────────────────────────┐  │
  │  │                                                              │  │
  │  │  Copilot Chat                                                │  │
  │  │  ┌────────────────────────────────────────────────────────┐  │  │
  │  │  │ User: "List topics on kafka-prod"                      │  │  │
  │  │  │ Copilot: Using tool list-topics from kafka-prod...     │  │  │
  │  │  │ Result: orders, payments, users, inventory (4 topics)  │  │  │
  │  │  └────────────────────────────────────────────────────────┘  │  │
  │  │                                                              │  │
  │  │  Built-in MCP Client (reads mcp.json, manages connections)  │  │
  │  │  ┌────────────────────────────────────────────────────────┐  │  │
  │  │  │ HTTP Connection → kafka-dev      (🟢 connected)        │  │  │
  │  │  │ HTTP Connection → kafka-sit      (🟢 connected)        │  │  │
  │  │  │ HTTP Connection → kafka-uat      (🟢 connected)        │  │  │
  │  │  │ HTTP Connection → kafka-prod     (🟢 connected)        │  │  │
  │  │  └────────────────────────────────────────────────────────┘  │  │
  │  │                                                              │  │
  │  │  Our Auth Extension (writes mcp.json, manages tokens)       │  │
  │  │  ┌────────────────────────────────────────────────────────┐  │  │
  │  │  │ Status: 🟢 MCP: developer@company.com                 │  │  │
  │  │  │ Token expires: 52 minutes (auto-refresh scheduled)     │  │  │
  │  │  │ Servers: 5 Kafka clusters configured                   │  │  │
  │  │  └────────────────────────────────────────────────────────┘  │  │
  │  │                                                              │  │
  │  └──────────────────────────────────────────────────────────────┘  │
  │                                                                    │
  │  ~/.vscode/mcp.json (auto-generated by our extension)              │
  │  ┌──────────────────────────────────────────────────────────────┐  │
  │  │ {                                                            │  │
  │  │   "servers": {                                               │  │
  │  │     "kafka-dev":       { url, headers: {Authorization} },   │  │
  │  │     "kafka-sit":       { url, headers: {Authorization} },   │  │
  │  │     "kafka-uat":       { url, headers: {Authorization} },   │  │
  │  │     "kafka-prod":      { url, headers: {Authorization} }    │  │
  │  │   }                                                          │  │
  │  │ }                                                            │  │
  │  └──────────────────────────────────────────────────────────────┘  │
  │                                                                    │
  │  OS Keychain (macOS Keychain / Windows Credential Manager)         │
  │  ┌──────────────────────────────────────────────────────────────┐  │
  │  │ mcp-access-token:  eyJhbGciOiJSUzI1NiIs... (JWT, 1hr)      │  │
  │  │ mcp-refresh-token: dGhpcyBpcyBhIHJlZnJl... (opaque, 30d)   │  │
  │  │ mcp-token-expiry:  1739451234567 (timestamp)                │  │
  │  │ mcp-user-email:    developer@company.com                    │  │
  │  └──────────────────────────────────────────────────────────────┘  │
  │                                                                    │
  └────────────────────────────────────────────────────────────────────┘
        │
        │ SSE connections with Authorization: Bearer <JWT>
        ▼
  ┌─────────────────────┐
  │   API Gateway        │ ← validates JWT locally (cached JWKS)
  │   mcp.company.com    │
  └─────────────────────┘
        │
        ├── /kafka/dev       → Kafka Dev MCP Server
        ├── /kafka/staging   → Kafka Staging MCP Server
        ├── /kafka/prod      → Kafka Prod MCP Server
        ├── /kafka/prod-eu   → Kafka Prod EU MCP Server
        └── /kafka/prod-west → Kafka Prod West MCP Server

  HOW VS CODE USES mcp.json WITH THE MCP TRANSPORT:

    When mcp.json has type "http" (Streamable HTTP — our choice):
      1. VS Code sends HTTPS POST to URL with tool call body
      2. Sends Authorization: Bearer <JWT> header on every POST
      3. Server responds with JSON body (or SSE stream for streaming)
      4. No persistent connection — pure request/response
      5. Stateless, works perfectly with K8s HPA and load balancers

    When mcp.json has type "sse":
      1. VS Code opens HTTPS GET request to URL (persistent SSE connection)
      2. Sends Authorization: Bearer <JWT> header on the GET request
      3. MCP server responds with SSE event stream (keeps connection open)
      4. Server pushes capabilities + tool list over the stream
      5. Requires connection state management, harder to scale

    ✅ We use "http" (Streamable HTTP) because:
       - Kafka operations are fast (<500ms) — no streaming needed
       - Stateless = works with K8s Deployment + HPA
       - Kong can route any request to any pod (no sticky sessions)
       - Simpler debugging (standard HTTP request/response)

  KEY INSIGHT: The token appears in TWO places on the developer's laptop:
    1. OS Keychain (SecretStorage) — source of truth, encrypted
    2. mcp.json (Authorization header) — so VS Code's MCP client can use it
  
  When the token refreshes, our extension:
    1. Stores new token in OS Keychain
    2. Rewrites mcp.json with new token
    3. VS Code detects file change → reconnects SSE with new token
  
  The developer sees NOTHING during this process. Fully silent.

  ┌─────────────────────────────────────────────────────────────────────┐
  │  ZERO-TOUCH mcp.json MANAGEMENT                                    │
  │                                                                     │
  │  The developer NEVER edits mcp.json manually. EVER.                │
  │                                                                     │
  │  The extension creates, updates, and manages it completely:         │
  │                                                                     │
  │  • First sign-in → extension CREATES mcp.json from scratch         │
  │  • Token refresh (~50 min) → extension REWRITES mcp.json silently  │
  │    VS Code detects change → reconnects all SSE sessions → done     │
  │  • Admin adds a Kafka cluster → extension APPENDS to mcp.json      │
  │  • Sign-out → extension REMOVES auth headers from mcp.json         │
  │  • Re-sign-in → extension RESTORES auth headers in mcp.json        │
  │  • VS Code restart → extension REGENERATES mcp.json from keychain  │
  │                                                                     │
  │  If a developer accidentally deletes or corrupts mcp.json,         │
  │  the extension detects this on the next refresh cycle and           │
  │  RECREATES it automatically. Self-healing.                          │
  │                                                                     │
  │  Developer interaction with mcp.json: NONE                          │
  │  Developer awareness of mcp.json: OPTIONAL (it just works)         │
  └─────────────────────────────────────────────────────────────────────┘
  → Read settings:
    const config = vscode.workspace.getConfiguration('mcpAuth')
    const gatewayUrl = config.get('gatewayUrl')  // https://mcp.company.com
    const servers = config.get('servers')          // array of {name, path, label}

  → Build mcp.json content:

    NOTE ON MCP TRANSPORT TYPES:
    ┌──────────────────────────────────────────────────────────────────┐
    │ MCP defines 3 transport types for VS Code ↔ MCP server:        │
    │                                                                  │
    │ "stdio"   Local only. Server runs as subprocess on laptop.     │
    │           VS Code communicates via stdin/stdout.                │
    │           NOT used for remote/cloud MCP servers.                │
    │                                                                  │
    │ "sse"     Remote. VS Code opens persistent SSE (Server-Sent    │
    │           Events) connection over HTTPS. Server pushes events  │
    │           down the stream. Client sends tool calls via POST.   │
    │           Requires persistent connections, harder to scale.    │
    │                                                                  │
    │ "http"    Remote. Streamable HTTP (MCP spec 2025+). Standard   │
    │           POST requests. Server responds with JSON or optional │
    │           SSE stream for long-running ops. Simpler, stateless, │
    │           scales better with Kubernetes HPA. Our choice.       │
    │                                                                  │
    │ ✅ We use "http" (Streamable HTTP) in mcp.json:                │
    │    - Kafka operations are fast (<500ms) — no streaming needed │
    │    - Stateless — works with K8s HPA and load balancers        │
    │    - Simpler debugging (standard HTTP request/response)        │
    │    - No connection state to manage                              │
    └──────────────────────────────────────────────────────────────────┘

    const mcpConfig = { servers: {} }
    
    for (const server of servers) {
      mcpConfig.servers[server.name] = {
        // "http" = Streamable HTTP transport (stateless, scalable)
        // Kafka operations are fast (<500ms), no streaming needed
        type: "http",
        url: `${gatewayUrl}${server.path}`,
        headers: {
          "Authorization": `Bearer ${accessToken}`
        }
      }
    }

    // Result (example with 4 Kafka environments):
    {
      "servers": {
        "kafka-dev": {
          "type": "http",
          "url": "https://mcp.company.com/kafka/dev",
          "headers": {
            "Authorization": "Bearer eyJhbGci..."
          }
        },
        "kafka-sit": {
          "type": "http",
          "url": "https://mcp.company.com/kafka/sit",
          "headers": {
            "Authorization": "Bearer eyJhbGci..."
          }
        },
        "kafka-uat": {
          "type": "http",
          "url": "https://mcp.company.com/kafka/uat",
          "headers": {
            "Authorization": "Bearer eyJhbGci..."
          }
        },
        "kafka-prod": {
          "type": "http",
          "url": "https://mcp.company.com/kafka/prod",
          "headers": {
            "Authorization": "Bearer eyJhbGci..."
          }
        }
      }
    }
          "type": "sse",
          "url": "https://mcp.company.com/kafka/prod-west",
          "headers": {
            "Authorization": "Bearer eyJhbGci..."
          }
        }
      }
    }

  → Write to file:
    If mcpAuth.mcpConfigScope === "user":
      Write to ~/.vscode/mcp.json (user-level, applies to all workspaces)
    If mcpAuth.mcpConfigScope === "workspace":
      Write to {workspaceRoot}/.vscode/mcp.json

  → IMPORTANT: If mcp.json already exists, MERGE — don't overwrite.
    Preserve any manual server entries the developer may have added.
    Only update entries that match our server names.

  → Show notification: "✅ Connected to Company MCP (N servers available)"
    (where N = number of servers in mcpAuth.servers, e.g., 5 Kafka clusters)
  → Update status bar: "🟢 MCP: user@company.com"

STEP 7: Background token refresh (runs silently, every 50 minutes)
──────────────────────────────────────────────────────────────────
  → Start interval timer on activation:
    const refreshInterval = setInterval(async () => {
      await checkAndRefreshToken()
    }, 50 * 60 * 1000)  // 50 minutes

  → checkAndRefreshToken():
    const expiry = Number(await context.secrets.get('mcp-token-expiry'))
    const tenMinutesMs = 10 * 60 * 1000

    if (Date.now() > expiry - tenMinutesMs) {
      // Token expires within 10 minutes — refresh now
      
      const refreshToken = await context.secrets.get('mcp-refresh-token')
      
      POST https://sso.company.com/as/token.oauth2
      Content-Type: application/x-www-form-urlencoded

      grant_type=refresh_token
      &refresh_token={refreshToken}
      &client_id=mcp-vscode

      IF success:
        → Store new access_token in SecretStorage
        → Store new expiry in SecretStorage
        → Store new refresh_token if returned
        → Regenerate mcp.json with new token (STEP 6)
        → Developer sees NOTHING ✅

      IF failure (401 — refresh token expired):
        → Show notification: 
          "MCP session expired (inactive for 30+ days). Please sign in again."
          [Sign In] [Dismiss]
        → Update status bar: "🔴 MCP: Session expired"
        → Clear all tokens from SecretStorage
    }

STEP 8: Watch settings for new MCP servers
──────────────────────────────────────────
  → Register watcher:
    vscode.workspace.onDidChangeConfiguration(event => {
      if (event.affectsConfiguration('mcpAuth.servers') ||
          event.affectsConfiguration('mcpAuth.gatewayUrl')) {
        regenerateMcpJson()
      }
    })

  → When admin pushes new server to settings (e.g., via Settings Sync):
    Before: ["kafka-dev", "kafka-sit", "kafka-uat", "kafka-prod"]
    After:  [...same..., "kafka-analytics"]  ← new cluster added

    Extension detects change → adds kafka-analytics to mcp.json with current token
    → NO re-authentication needed
    → NO notification to developer (silent)
    → Developer can immediately use: "List topics on kafka-analytics" in Copilot Chat

  → Also works for adding entirely new server types:
    After:  [...same..., "database-mcp", "redis-mcp"]
    → Same flow — all use the same JWT token

STEP 9: Handle edge cases
─────────────────────────
  SIGN OUT (command: "MCP Auth: Sign Out"):
    → Delete all tokens from SecretStorage
    → Remove auth headers from mcp.json (keep server entries, remove headers)
    → Clear refresh interval
    → Update status bar: "🔴 MCP: Sign in required"

  PINGFEDERATE UNREACHABLE (during sign-in or refresh):
    → Retry with exponential backoff: 5s, 10s, 20s, 40s, 80s
    → During retry, status bar: "🟡 MCP: Reconnecting..."
    → After 5 retries: "🔴 MCP: Offline (SSO unreachable)"
    → Keep retry timer active (recover when network restored)

  MULTIPLE VS CODE WINDOWS:
    → SecretStorage is shared across windows (VS Code handles this)
    → All windows use the same token
    → Refresh in one window updates all windows

  CORRUPTED TOKEN / INVALID JWT:
    → Try refresh token first
    → If refresh fails: prompt sign-in
    → Log error to output channel (not the token itself — never log tokens)

  EXTENSION UPDATE:
    → Tokens persist in SecretStorage across updates
    → mcp.json regenerated on activation
    → No re-authentication needed

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PROJECT STRUCTURE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mcp-auth-extension/
├── .vscode/
│   ├── launch.json             # F5 to run extension in debug mode
│   └── tasks.json              # Compile TypeScript
├── .vscodeignore                # Files to exclude from VSIX package
├── .gitignore
├── package.json                 # Extension manifest (see below)
├── tsconfig.json                # TypeScript strict mode
├── README.md                    # Marketplace description
├── CHANGELOG.md                 # Version history
├── LICENSE                      # Internal/proprietary
├── src/
│   ├── extension.ts             # Entry point: activate(), deactivate()
│   ├── auth/
│   │   ├── authProvider.ts      # AuthenticationProvider implementation
│   │   ├── pkce.ts              # PKCE code_verifier + code_challenge
│   │   ├── callbackServer.ts    # Local HTTP server for OAuth callback
│   │   └── tokenManager.ts      # Store, read, refresh, clear tokens
│   ├── config/
│   │   ├── mcpConfigManager.ts  # Generate/update .vscode/mcp.json
│   │   └── settingsWatcher.ts   # Watch for new MCP servers in settings
│   ├── ui/
│   │   ├── statusBar.ts         # 🟢/🔴/🟡 connection status
│   │   └── notifications.ts    # Sign-in prompts, success/error messages
│   └── utils/
│       ├── jwtParser.ts         # Parse JWT payload (email, teams, expiry)
│       └── logger.ts            # Output channel logging (NEVER log tokens)
├── resources/
│   ├── icon.png                 # Extension icon (128x128)
│   └── callback.html            # "Auth successful" page for browser
└── test/
    ├── suite/
    │   ├── extension.test.ts    # Integration tests
    │   ├── tokenManager.test.ts # Token lifecycle tests  
    │   ├── pkce.test.ts         # PKCE generation tests
    │   └── mcpConfig.test.ts    # mcp.json generation tests
    └── runTest.ts               # Test runner

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PACKAGE.JSON (COMPLETE)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

{
  "name": "mcp-auth",
  "displayName": "Company MCP Auth",
  "description": "Centralized SSO auth for 100–1,000 developers accessing Kafka clusters across AWS regions via MCP",
  "version": "1.0.0",
  "publisher": "company-name",
  "engines": {
    "vscode": "^1.85.0"
  },
  "categories": ["Other"],
  "keywords": ["MCP", "SSO", "PingFederate", "Kafka", "Authentication", "Enterprise", "Multi-Region"],
  "icon": "resources/icon.png",
  "activationEvents": ["onStartupFinished"],
  "main": "./out/extension.js",
  "contributes": {
    "authentication": [
      {
        "id": "company-sso",
        "label": "Company SSO (PingFederate)"
      }
    ],
    "commands": [
      {
        "command": "mcp-auth.signIn",
        "title": "MCP Auth: Sign In",
        "icon": "$(sign-in)"
      },
      {
        "command": "mcp-auth.signOut",
        "title": "MCP Auth: Sign Out",
        "icon": "$(sign-out)"
      },
      {
        "command": "mcp-auth.status",
        "title": "MCP Auth: Show Connection Status",
        "icon": "$(info)"
      },
      {
        "command": "mcp-auth.refreshServers",
        "title": "MCP Auth: Refresh Server Configuration",
        "icon": "$(refresh)"
      }
    ],
    "configuration": {
      "title": "MCP Auth",
      "properties": {
        "mcpAuth.gatewayUrl": {
          "type": "string",
          "default": "https://mcp.company.com",
          "description": "MCP API Gateway URL — single entry point for all MCP servers"
        },
        "mcpAuth.ssoUrl": {
          "type": "string",
          "default": "https://sso.company.com",
          "description": "PingFederate SSO base URL"
        },
        "mcpAuth.clientId": {
          "type": "string",
          "default": "mcp-vscode",
          "description": "OAuth 2.0 client ID registered in PingFederate (public client, no secret)"
        },
        "mcpAuth.servers": {
          "type": "array",
          "description": "MCP servers behind the API gateway. All share the same auth token. Each Kafka cluster (dev, sit, uat, prod) is a separate entry. Add entries here and developers get immediate access — no re-authentication.",
          "default": [
            { "name": "kafka-dev",  "path": "/kafka/dev",  "label": "Kafka Dev" },
            { "name": "kafka-sit",  "path": "/kafka/sit",  "label": "Kafka SIT" },
            { "name": "kafka-uat",  "path": "/kafka/uat",  "label": "Kafka UAT" },
            { "name": "kafka-prod", "path": "/kafka/prod", "label": "Kafka Prod" }
          ],
          "items": {
            "type": "object",
            "properties": {
              "name": {
                "type": "string",
                "description": "Unique identifier for this MCP server (used as key in mcp.json)"
              },
              "path": {
                "type": "string",
                "description": "URL path on the API gateway (e.g., /kafka, /database)"
              },
              "label": {
                "type": "string",
                "description": "Human-readable display name"
              }
            },
            "required": ["name", "path", "label"]
          }
        },
        "mcpAuth.tokenRefreshIntervalMinutes": {
          "type": "number",
          "default": 50,
          "description": "How often to check if token needs refresh (minutes)"
        },
        "mcpAuth.mcpConfigScope": {
          "type": "string",
          "enum": ["user", "workspace"],
          "default": "user",
          "description": "Where to write mcp.json — user-level (global) or workspace-level"
        }
      }
    }
  },
  "scripts": {
    "vscode:prepublish": "npm run compile",
    "compile": "tsc -p ./",
    "watch": "tsc -watch -p ./",
    "pretest": "npm run compile",
    "test": "node ./out/test/runTest.js",
    "package": "vsce package",
    "publish": "vsce publish"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "@types/vscode": "^1.85.0",
    "@vscode/test-electron": "^2.3.0",
    "typescript": "^5.3.0",
    "@vscode/vsce": "^2.22.0"
  }
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SOURCE FILES — DETAILED SPECIFICATIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

FILE: src/extension.ts
─────────────────────
Purpose: Entry point. Wire everything together.

export async function activate(context: vscode.ExtensionContext) {
  // 1. Create instances
  const tokenManager = new TokenManager(context.secrets)
  const authProvider = new AuthProvider(tokenManager, context)
  const mcpConfigManager = new McpConfigManager(tokenManager)
  const statusBar = new StatusBar()
  const settingsWatcher = new SettingsWatcher(mcpConfigManager)

  // 2. Register authentication provider
  context.subscriptions.push(
    vscode.authentication.registerAuthenticationProvider(
      'company-sso',
      'Company SSO (PingFederate)',
      authProvider,
      { supportsMultipleAccounts: false }
    )
  )

  // 3. Register commands
  context.subscriptions.push(
    vscode.commands.registerCommand('mcp-auth.signIn', () => authProvider.signIn()),
    vscode.commands.registerCommand('mcp-auth.signOut', () => authProvider.signOut()),
    vscode.commands.registerCommand('mcp-auth.status', () => showStatus(tokenManager)),
    vscode.commands.registerCommand('mcp-auth.refreshServers', () => mcpConfigManager.regenerate())
  )

  // 4. Check existing auth state
  if (await tokenManager.hasValidToken()) {
    const email = await tokenManager.getUserEmail()
    statusBar.setConnected(email)
    await mcpConfigManager.ensureConfigUpToDate()
  } else if (await tokenManager.canRefresh()) {
    const refreshed = await tokenManager.refreshAccessToken()
    if (refreshed) {
      const email = await tokenManager.getUserEmail()
      statusBar.setConnected(email)
      await mcpConfigManager.regenerate()
    } else {
      statusBar.setDisconnected()
      showSignInNotification()
    }
  } else {
    statusBar.setDisconnected()
    showSignInNotification()
  }

  // 5. Start background token refresh
  tokenManager.startAutoRefresh(async (newToken) => {
    await mcpConfigManager.updateToken(newToken)
  })

  // 6. Watch for settings changes (new MCP servers added)
  settingsWatcher.start()

  // 7. Register disposables
  context.subscriptions.push(statusBar, settingsWatcher)
}

export function deactivate() {
  // Cleanup handled by disposables
}

───────────────────────────────────────────────────────────────────────

FILE: src/auth/authProvider.ts
──────────────────────────────
Purpose: Implement vscode.AuthenticationProvider.

class AuthProvider implements vscode.AuthenticationProvider {
  // Track sessions
  private _onDidChangeSessions = new vscode.EventEmitter<AuthenticationProviderAuthenticationSessionsChangeEvent>()
  readonly onDidChangeSessions = this._onDidChangeSessions.event

  constructor(
    private tokenManager: TokenManager,
    private context: vscode.ExtensionContext
  ) {}

  async getSessions(): Promise<vscode.AuthenticationSession[]> {
    // Return current session if token exists
    const token = await this.tokenManager.getAccessToken()
    if (!token) return []
    
    const email = await this.tokenManager.getUserEmail()
    return [{
      id: 'company-mcp-session',
      accessToken: token,
      account: { id: email, label: email },
      scopes: ['openid', 'profile', 'email', 'mcp-access']
    }]
  }

  async createSession(scopes: string[]): Promise<vscode.AuthenticationSession> {
    // Trigger full OAuth PKCE flow
    return this.signIn()
  }

  async removeSession(sessionId: string): Promise<void> {
    return this.signOut()
  }

  async signIn(): Promise<vscode.AuthenticationSession> {
    // 1. Generate PKCE
    const { codeVerifier, codeChallenge } = generatePKCE()
    const state = generateState()
    
    // 2. Start callback server
    const { port, waitForCallback } = await startCallbackServer(state)
    
    // 3. Build auth URL
    const config = vscode.workspace.getConfiguration('mcpAuth')
    const ssoUrl = config.get<string>('ssoUrl')
    const clientId = config.get<string>('clientId')
    
    const authUrl = buildAuthUrl(ssoUrl, clientId, port, codeChallenge, state)
    
    // 4. Open browser
    await vscode.env.openExternal(vscode.Uri.parse(authUrl))
    
    // 5. Wait for callback
    const authCode = await waitForCallback
    
    // 6. Exchange code for tokens
    const tokens = await exchangeCodeForTokens(ssoUrl, clientId, authCode, port, codeVerifier)
    
    // 7. Store tokens
    await this.tokenManager.storeTokens(tokens)
    
    // 8. Fire event
    const session = (await this.getSessions())[0]
    this._onDidChangeSessions.fire({ added: [session], removed: [], changed: [] })
    
    return session
  }

  async signOut(): Promise<void> {
    await this.tokenManager.clearTokens()
    this._onDidChangeSessions.fire({ added: [], removed: [], changed: [] })
  }
}

───────────────────────────────────────────────────────────────────────

FILE: src/auth/pkce.ts
──────────────────────
Purpose: Generate PKCE values for OAuth 2.0 public client auth.

import * as crypto from 'crypto'

function base64url(buffer: Buffer): string {
  return buffer.toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

export function generatePKCE(): { codeVerifier: string; codeChallenge: string } {
  const codeVerifier = base64url(crypto.randomBytes(32))
  const codeChallenge = base64url(
    crypto.createHash('sha256').update(codeVerifier).digest()
  )
  return { codeVerifier, codeChallenge }
}

export function generateState(): string {
  return crypto.randomBytes(16).toString('hex')
}

───────────────────────────────────────────────────────────────────────

FILE: src/auth/callbackServer.ts
────────────────────────────────
Purpose: Temporary local HTTP server to receive OAuth callback.

import * as http from 'http'
import * as url from 'url'
import * as fs from 'fs'
import * as path from 'path'

export async function startCallbackServer(expectedState: string): Promise<{
  port: number
  waitForCallback: Promise<string>  // resolves with auth code
}> {
  return new Promise((resolvePort) => {
    let resolveCode: (code: string) => void
    let rejectCode: (error: Error) => void
    
    const waitForCallback = new Promise<string>((resolve, reject) => {
      resolveCode = resolve
      rejectCode = reject
    })

    const server = http.createServer((req, res) => {
      const parsedUrl = url.parse(req.url!, true)
      
      if (parsedUrl.pathname === '/callback') {
        const { code, state, error } = parsedUrl.query
        
        if (error) {
          res.writeHead(400, { 'Content-Type': 'text/html' })
          res.end('<h1>Authentication Failed</h1><p>Please try again.</p>')
          rejectCode(new Error(`OAuth error: ${error}`))
          server.close()
          return
        }
        
        if (state !== expectedState) {
          res.writeHead(400, { 'Content-Type': 'text/html' })
          res.end('<h1>Security Error</h1><p>State mismatch. Please try again.</p>')
          rejectCode(new Error('CSRF: state mismatch'))
          server.close()
          return
        }
        
        // Success — send response page
        res.writeHead(200, { 'Content-Type': 'text/html' })
        res.end(`
          <html>
          <body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; 
                       text-align: center; padding: 60px; color: #333;">
            <h1>✅ Authentication Successful</h1>
            <p style="color: #666;">You can close this window and return to VS Code.</p>
            <script>setTimeout(() => window.close(), 3000)</script>
          </body>
          </html>
        `)
        
        resolveCode(code as string)
        
        // Close server after short delay (let response send)
        setTimeout(() => server.close(), 1000)
      }
    })

    // Listen on random port, localhost only
    server.listen(0, '127.0.0.1', () => {
      const address = server.address() as { port: number }
      resolvePort({ port: address.port, waitForCallback })
    })

    // Timeout after 5 minutes (user abandoned auth)
    setTimeout(() => {
      rejectCode(new Error('Authentication timed out'))
      server.close()
    }, 5 * 60 * 1000)
  })
}

───────────────────────────────────────────────────────────────────────

FILE: src/auth/tokenManager.ts
──────────────────────────────
Purpose: Manage token lifecycle — store, read, refresh, clear.

export class TokenManager {
  private refreshTimer: NodeJS.Timeout | undefined

  constructor(private secrets: vscode.SecretStorage) {}

  async storeTokens(tokens: OAuthTokens): Promise<void> {
    await this.secrets.store('mcp-access-token', tokens.access_token)
    await this.secrets.store('mcp-refresh-token', tokens.refresh_token)
    await this.secrets.store('mcp-token-expiry', 
      String(Date.now() + tokens.expires_in * 1000))
    
    // Parse email from ID token or access token
    const payload = this.parseJwt(tokens.id_token || tokens.access_token)
    await this.secrets.store('mcp-user-email', payload.email)
  }

  async getAccessToken(): Promise<string | undefined> {
    return this.secrets.get('mcp-access-token')
  }

  async getUserEmail(): Promise<string> {
    return (await this.secrets.get('mcp-user-email')) || 'unknown'
  }

  async hasValidToken(): Promise<boolean> {
    const token = await this.getAccessToken()
    const expiry = await this.secrets.get('mcp-token-expiry')
    if (!token || !expiry) return false
    return Date.now() < Number(expiry)
  }

  async canRefresh(): Promise<boolean> {
    const refreshToken = await this.secrets.get('mcp-refresh-token')
    return !!refreshToken
  }

  async refreshAccessToken(): Promise<boolean> {
    const refreshToken = await this.secrets.get('mcp-refresh-token')
    if (!refreshToken) return false

    const config = vscode.workspace.getConfiguration('mcpAuth')
    const ssoUrl = config.get<string>('ssoUrl')!
    const clientId = config.get<string>('clientId')!

    try {
      const response = await fetch(`${ssoUrl}/as/token.oauth2`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          refresh_token: refreshToken,
          client_id: clientId
        })
      })

      if (!response.ok) {
        // Refresh token expired — need full re-auth
        await this.clearTokens()
        return false
      }

      const tokens = await response.json()
      await this.storeTokens(tokens)
      return true
    } catch (error) {
      // Network error — don't clear tokens, retry later
      return false
    }
  }

  startAutoRefresh(onRefresh: (token: string) => Promise<void>): void {
    const config = vscode.workspace.getConfiguration('mcpAuth')
    const intervalMin = config.get<number>('tokenRefreshIntervalMinutes', 50)
    
    // SCALE: Add random jitter (0-60 seconds) to prevent thundering herd.
    // With 1,000 developers, without jitter ALL would refresh at exactly
    // the same second, hammering PingFederate with 1,000 concurrent requests.
    const jitterMs = Math.floor(Math.random() * 60 * 1000)
    
    this.refreshTimer = setInterval(async () => {
      // Add per-check jitter as well
      await new Promise(r => setTimeout(r, Math.floor(Math.random() * 10_000)))
      
      const expiry = Number(await this.secrets.get('mcp-token-expiry'))
      const tenMinutes = 10 * 60 * 1000
      
      if (Date.now() > expiry - tenMinutes) {
        const success = await this.refreshAccessToken()
        if (success) {
          const token = await this.getAccessToken()
          if (token) await onRefresh(token)
        }
      }
    }, intervalMin * 60 * 1000 + jitterMs)
  }

  async clearTokens(): Promise<void> {
    await this.secrets.delete('mcp-access-token')
    await this.secrets.delete('mcp-refresh-token')
    await this.secrets.delete('mcp-token-expiry')
    await this.secrets.delete('mcp-user-email')
    if (this.refreshTimer) clearInterval(this.refreshTimer)
  }

  private parseJwt(token: string): any {
    const payload = token.split('.')[1]
    return JSON.parse(Buffer.from(payload, 'base64').toString())
  }
}

───────────────────────────────────────────────────────────────────────

FILE: src/config/mcpConfigManager.ts
────────────────────────────────────
Purpose: Generate and update .vscode/mcp.json for all MCP servers.

export class McpConfigManager {
  constructor(private tokenManager: TokenManager) {}

  async regenerate(): Promise<void> {
    const token = await this.tokenManager.getAccessToken()
    if (!token) return

    const config = vscode.workspace.getConfiguration('mcpAuth')
    const gatewayUrl = config.get<string>('gatewayUrl')!
    const servers = config.get<McpServer[]>('servers')!
    const scope = config.get<string>('mcpConfigScope', 'user')

    // Build MCP config
    const mcpConfig: any = { servers: {} }
    
    for (const server of servers) {
      mcpConfig.servers[server.name] = {
        type: "sse",
        url: `${gatewayUrl}${server.path}`,
        headers: {
          "Authorization": `Bearer ${token}`
        }
      }
    }

    // Determine path
    const configPath = scope === 'user'
      ? path.join(os.homedir(), '.vscode', 'mcp.json')
      : path.join(vscode.workspace.workspaceFolders![0].uri.fsPath, '.vscode', 'mcp.json')

    // Merge with existing (don't overwrite manual entries)
    let existing: any = {}
    if (fs.existsSync(configPath)) {
      existing = JSON.parse(fs.readFileSync(configPath, 'utf8'))
    }
    
    // Update only our managed servers
    if (!existing.servers) existing.servers = {}
    for (const [name, config] of Object.entries(mcpConfig.servers)) {
      existing.servers[name] = config
    }

    // Write
    const dir = path.dirname(configPath)
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(configPath, JSON.stringify(existing, null, 2))
  }

  async updateToken(newToken: string): Promise<void> {
    // Same as regenerate but uses the new token
    await this.regenerate()
  }

  async ensureConfigUpToDate(): Promise<void> {
    await this.regenerate()
  }

  async removeAuthHeaders(): Promise<void> {
    // On sign-out: remove auth headers but keep server entries
    // so mcp.json structure is preserved
    await this.regenerate()  // Will be empty since no token
  }
}

───────────────────────────────────────────────────────────────────────

FILE: src/config/settingsWatcher.ts
───────────────────────────────────
Purpose: Watch for changes to extension settings.

export class SettingsWatcher implements vscode.Disposable {
  private disposable: vscode.Disposable | undefined

  constructor(private mcpConfigManager: McpConfigManager) {}

  start(): void {
    this.disposable = vscode.workspace.onDidChangeConfiguration(event => {
      if (event.affectsConfiguration('mcpAuth.servers') ||
          event.affectsConfiguration('mcpAuth.gatewayUrl')) {
        // Admin added/removed MCP server — regenerate mcp.json
        // No re-authentication needed
        this.mcpConfigManager.regenerate()
      }
    })
  }

  dispose(): void {
    this.disposable?.dispose()
  }
}

───────────────────────────────────────────────────────────────────────

FILE: src/ui/statusBar.ts
─────────────────────────
Purpose: Show connection status in VS Code status bar.

export class StatusBar implements vscode.Disposable {
  private item: vscode.StatusBarItem

  constructor() {
    this.item = vscode.window.createStatusBarItem(
      vscode.StatusBarAlignment.Left, 100
    )
    this.item.command = 'mcp-auth.status'
    this.item.show()
  }

  setConnected(email: string): void {
    this.item.text = `$(pass-filled) MCP: ${email}`
    this.item.tooltip = `Connected to Company MCP as ${email}`
    this.item.backgroundColor = undefined
  }

  setDisconnected(): void {
    this.item.text = '$(circle-slash) MCP: Sign in required'
    this.item.tooltip = 'Click to sign in to Company MCP'
    this.item.command = 'mcp-auth.signIn'
    this.item.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground')
  }

  setReconnecting(): void {
    this.item.text = '$(sync~spin) MCP: Reconnecting...'
    this.item.tooltip = 'Attempting to reconnect...'
  }

  setOffline(): void {
    this.item.text = '$(circle-slash) MCP: Offline'
    this.item.tooltip = 'SSO server unreachable'
    this.item.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground')
  }

  dispose(): void {
    this.item.dispose()
  }
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ADMIN WORKFLOW: ADDING A NEW MCP SERVER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

When engineering team deploys a new Kafka cluster or MCP server:

Example A: New Kafka cluster (kafka-analytics in us-east-1)
─────────────────────────────────────────────────────────────
  Step 1: Deploy Kafka MCP server pointing to new MSK cluster
  Step 2: Add gateway route: /kafka/analytics → Kafka Analytics MCP pod
  Step 3: Push settings update to 100–1,000 developers:

    Method A: Update extension default settings (requires extension publish)
      → Add { "name": "kafka-analytics", "path": "/kafka/analytics", "label": "Kafka Analytics (us-east-1)" }
      → Publish extension update → VS Code auto-updates

    Method B: Push via VS Code Settings Sync (company-managed)
      → Update company-managed settings policy → Syncs to all machines

    Method C: Push via MDM/configuration management
      → Update settings.json via JAMF, Intune, etc.

  Step 4: Extension detects change, adds to mcp.json with current token
  Step 5: Developers can immediately use: "List topics on kafka-analytics"

Example B: New server type (Database MCP)
──────────────────────────────────────────
  Step 1: Deploy Database MCP server behind same API gateway (NO auth code)
  Step 2: Add gateway route: /database → Database MCP pod
  Step 3: Push settings update with { "name": "database-mcp", "path": "/database", "label": "Database" }
  Step 4–5: Same as above — zero re-auth, immediate access

Developer effort: ZERO (for all 100–1,000 developers)
Developer re-authentication: NONE
Time to access: INSTANT (next VS Code restart or settings sync)

Scale note: Settings Sync pushes to all developers simultaneously.
No per-developer configuration needed. One update → 1,000 developers get access.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SECURITY REQUIREMENTS (NON-NEGOTIABLE)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1.  PKCE is MANDATORY (public client, no client_secret)
2.  Tokens stored ONLY in VS Code SecretStorage (OS keychain)
3.  NEVER log tokens to console, output channel, telemetry, or any file
4.  NEVER store tokens in settings.json or any readable location
5.  State parameter for CSRF protection on every OAuth callback
6.  Callback server binds ONLY to 127.0.0.1 (NOT 0.0.0.0)
7.  Callback server shuts down immediately after receiving code
8.  Callback server has 5-minute timeout (abort if user abandons)
9.  HTTPS for all PingFederate communication (never HTTP)
10. Clear ALL tokens on sign-out (access, refresh, expiry, email)
11. Token refresh must be SILENT — never interrupt developer workflow
12. JWT parsing for display only — server-side validates signatures
13. No third-party dependencies for crypto (use Node.js built-in crypto)
14. Token refresh timer adds random jitter (0–60 seconds) to avoid
    thundering herd — 1,000 developers must NOT refresh at the same second
15. All PingFederate calls have 10-second timeout (don't hang on SSO outage)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TESTING CHECKLIST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Authentication:
[ ] First-time sign-in: notification → browser → SSO → success
[ ] Token stored in OS keychain after sign-in
[ ] Sign-out clears all tokens from keychain
[ ] Expired refresh token shows "sign in again" notification
[ ] PingFederate unreachable: retry with exponential backoff
[ ] OAuth state mismatch: rejected with error message
[ ] 5-minute timeout: server closes if user abandons auth

Token Lifecycle:
[ ] Auto-refresh at configured interval (default 50 min)
[ ] Refresh happens silently — no UI interruption
[ ] New token automatically updates mcp.json
[ ] Multiple VS Code windows share auth state
[ ] Extension restart: tokens loaded from keychain (no re-auth)
[ ] Extension update: tokens persist (no re-auth)

MCP Configuration:
[ ] mcp.json generated with all servers from settings
[ ] All servers have identical Authorization header (same token)
[ ] New server added to settings → mcp.json updated automatically
[ ] Existing manual entries in mcp.json preserved on update
[ ] mcp.json scope: user-level vs workspace-level works correctly

Cross-Platform:
[ ] macOS: Keychain Access integration works
[ ] Windows: Credential Manager integration works
[ ] Linux: libsecret / GNOME Keyring integration works

User Experience:
[ ] Status bar shows correct state (connected/disconnected/reconnecting)
[ ] Extension appears in VS Code Accounts menu (bottom-left avatar)
[ ] Works for fresh VS Code install
[ ] Works for existing VS Code with other extensions
[ ] No impact on GitHub Copilot, other extensions, or settings
[ ] Status bar click shows quick pick with options

Scale (100–1,000 developers, 4 Kafka environments):
[ ] Token refresh jitter: 1,000 developers don't all refresh at the same second
[ ] 4 Kafka environment entries generated correctly in mcp.json
[ ] Adding a 5th environment → mcp.json updated, no re-auth
[ ] Extension activates quickly with 4+ server entries
[ ] PingFederate handles concurrent auth from 100+ developers

Security:
[ ] Tokens never appear in console or output channel
[ ] Tokens never written to settings.json or any file
[ ] Callback server binds to 127.0.0.1 only
[ ] PKCE code_verifier is unique per auth attempt
[ ] State parameter is unique per auth attempt

30-Day Inactive Scenario:
[ ] After 30 days of inactivity, refresh token is invalid (expected)
[ ] Extension shows clear message: "Your session has expired. Please sign in again."
[ ] Click "Sign In" → browser opens → SSO → done in 30 seconds
[ ] Previous tokens cleared before new sign-in
```

---

## ✅ Expected Outcomes & Success Criteria

After implementing this extension, the following outcomes MUST be achieved:

### Developer Experience (100% of these must pass)

| Scenario | Expected Outcome |
|----------|------------------|
| **First Install** | Install extension → "Sign in to Company MCP" notification appears within 5 seconds |
| **First Sign-In** | Click "Sign In" → browser opens PingFederate → SSO login → "Connected" status in VS Code. Total time: < 60 seconds |
| **Daily Use** | Open VS Code → tokens loaded from keychain → tool calls work immediately. Zero interaction required |
| **Token Refresh** | Every ~50 minutes, token refreshes silently. Developer sees nothing, no interruption |
| **30-Day Return** | After 30+ days inactive → "Session expired. Please sign in again." → one-click re-auth (30 seconds) |
| **New MCP Server** | Admin pushes new server to settings → appears in mcp.json automatically → no re-auth needed |
| **Sign Out** | Run "MCP Auth: Sign Out" → all tokens cleared → status shows "Sign in required" |

### Technical Metrics (Verify in Tests)

| Metric | Target |
|--------|--------|
| Extension activation time | < 500ms (no blocking I/O on activation) |
| Token refresh latency | < 2 seconds (including PingFederate roundtrip) |
| mcp.json generation time | < 100ms (local file write only) |
| Memory footprint | < 10MB (tokens in memory + minimal state) |
| Lines of code | ~150-200 lines for AuthenticationProvider |

### Integration Verification

| Integration | How to Verify |
|-------------|---------------|
| VS Code Accounts menu | Extension appears under avatar icon (bottom-left) |
| Copilot Chat | "List topics on kafka-dev" returns result from MCP server |
| Kong Gateway | Request reaches MCP server with `X-User-Email` header |
| PingFederate | JWKS endpoint accessible, JWT validates correctly |
| OS Keychain | Token visible in Keychain Access (macOS) / Credential Manager (Windows) |

---

## 📋 Developer Implementation Checklist

Use this checklist to track implementation progress. Mark `[x]` when complete.

### Phase 1: Project Setup (Day 1)

- [ ] Create TypeScript VS Code extension project (`yo code`)
- [ ] Configure `package.json` with authentication contribution point
- [ ] Set up build pipeline (`npm run compile`, `npm run watch`)
- [ ] Create project structure (auth/, config/, ui/, utils/)
- [ ] Set up test framework (`npm run test`)

### Phase 2: Authentication Core (Days 2-3)

- [ ] **Implement AuthenticationProvider interface (~150 lines)**
  - [ ] `getSessions()`: Read token from memory cache, fallback to keychain
  - [ ] `createSession()`: Trigger OAuth PKCE flow
  - [ ] `removeSession()`: Clear tokens from keychain + memory
  - [ ] Fire `onDidChangeSessions` event on state changes

- [ ] **PKCE Implementation**
  - [ ] Generate `code_verifier` (43 chars, URL-safe base64)
  - [ ] Generate `code_challenge` (SHA-256 hash of verifier, base64url encoded)
  - [ ] Generate `state` parameter (16 bytes hex)

- [ ] **OAuth Callback Server**
  - [ ] Create temporary HTTP server on 127.0.0.1 (random port)
  - [ ] Parse callback: extract `code` and `state` params
  - [ ] Verify `state` matches (CSRF protection)
  - [ ] Return success HTML page with auto-close
  - [ ] 5-minute timeout, auto-close on success

- [ ] **Token Exchange**
  - [ ] POST to PingFederate token endpoint
  - [ ] Include `code_verifier` (PKCE proof)
  - [ ] Parse response: `access_token`, `refresh_token`, `expires_in`

### Phase 3: Token Management (Day 4)

- [ ] **Two-Tier Token Storage**
  - [ ] Store tokens in OS keychain via `context.secrets` (persistence)
  - [ ] Cache tokens in memory (speed, ~0ms reads)
  - [ ] On startup: read keychain → cache in memory
  - [ ] On refresh: update memory + keychain

- [ ] **Token Refresh Logic**
  - [ ] Start background timer (50 min default + random jitter 0-60s)
  - [ ] Check if token expires within 10 minutes
  - [ ] If expiring: POST to token endpoint with `refresh_token`
  - [ ] On success: update memory + keychain + mcp.json
  - [ ] On failure (401): clear tokens, show "Session expired" message

- [ ] **30-Day Scenario**
  - [ ] Detect 401 on refresh (refresh token expired)
  - [ ] Show notification: "Your session has expired. Please sign in again."
  - [ ] Clear all old tokens before new sign-in

### Phase 4: mcp.json Generation (Day 5)

- [ ] **Build mcp.json Content**
  - [ ] Read server list from settings (`mcpAuth.servers`)
  - [ ] For each server: create entry with `type: "http"`, URL, Authorization header
  - [ ] Read token from MEMORY (not keychain on every write)

- [ ] **File Management**
  - [ ] Determine path: user-level (`~/.vscode/mcp.json`) or workspace-level
  - [ ] Merge with existing entries (preserve manual entries)
  - [ ] Write atomically (temp file + rename)

- [ ] **Lifecycle Events**
  - [ ] On sign-in: generate mcp.json
  - [ ] On token refresh: update Authorization header
  - [ ] On sign-out: remove Authorization headers (keep server entries)
  - [ ] On settings change: regenerate mcp.json

### Phase 5: UI & User Experience (Day 6)

- [ ] **Status Bar**
  - [ ] Show connected state: `✅ MCP: user@company.com`
  - [ ] Show disconnected state: `🔴 MCP: Sign in required` (clickable)
  - [ ] Show reconnecting state: `🔄 MCP: Reconnecting...`

- [ ] **Notifications**
  - [ ] First-time prompt: "Sign in to Company MCP" with [Sign In] button
  - [ ] Success: "Connected to Company MCP (4 servers available)"
  - [ ] Session expired: "Your session has expired. Please sign in again."

- [ ] **Commands**
  - [ ] `mcp-auth.signIn`: Trigger sign-in flow
  - [ ] `mcp-auth.signOut`: Clear all tokens
  - [ ] `mcp-auth.status`: Show quick pick with status info

### Phase 6: Testing (Days 7-8)

- [ ] **Unit Tests**
  - [ ] PKCE generation produces correct format
  - [ ] State parameter is unique per call
  - [ ] JWT payload parsing extracts email, exp, etc.

- [ ] **Integration Tests**
  - [ ] Mock PingFederate responses
  - [ ] Full sign-in flow completes successfully
  - [ ] Token refresh updates mcp.json
  - [ ] Sign-out clears all state

- [ ] **Manual Testing**
  - [ ] Test on macOS, Windows, Linux
  - [ ] Verify Keychain Access / Credential Manager entries
  - [ ] Verify Copilot Chat can call MCP tools

### Phase 7: Documentation & Package (Day 9)

- [ ] Write README.md (installation, usage, troubleshooting)
- [ ] Create CHANGELOG.md
- [ ] Package extension: `vsce package` → `.vsix` file
- [ ] Test installation from `.vsix`
- [ ] Publish to internal Marketplace (or distribute `.vsix`)

---

## PingFederate Setup (Prerequisites)

Before the extension can work, PingFederate must be configured:

1. **Register OAuth Client:**
   - Client ID: `mcp-vscode`
   - Client Type: **Public** (no client secret)
   - Grant Types: Authorization Code
   - PKCE: Required, S256
   - Redirect URIs: `http://localhost:*/callback` (wildcard port)
   - Scopes: `openid`, `profile`, `email`, `mcp-access`
   - Access Token Type: JWT (signed with RS256)
   - Access Token Lifetime: 3600 seconds (1 hour)
   - Refresh Token Lifetime: 2592000 seconds (30 days)

2. **JWT Claims Mapping:**
   Configure PingFederate to include these claims in the access token JWT:
   - `sub`: User's unique ID
   - `email`: User's email address
   - `teams`: Array of team names (from Active Directory / LDAP groups)
   - `roles`: Array of role names
   - `aud`: `mcp-api` (single audience for all MCP servers)

3. **CORS:** Not needed (extension uses server-side HTTP calls, not browser fetch)

---

## How to Use This Prompt

1. **Copy the prompt section** (everything between the triple backticks)
2. **Open a new VS Code workspace** for the extension project
3. **Paste the prompt** into Copilot Chat or your AI coding agent
4. **Replace company-specific values:**
   - `company-name` → your actual company name
   - `https://sso.company.com` → your PingFederate URL
   - `https://mcp.company.com` → your MCP gateway URL
   - `mcp-vscode` → your registered OAuth client ID
5. **Let the AI build the extension**
6. **Test with:** `F5` in VS Code (launches Extension Development Host)
7. **Package:** `vsce package` → produces `.vsix` file
8. **Distribute:** Publish to internal Marketplace or share `.vsix` directly
